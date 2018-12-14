/*
 * Copyright 2018 Loopring Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.loopring.lightcone.actors.core

import akka.actor._
import akka.cluster.sharding._
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core.market.MarketManager.MatchResult
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._

import scala.concurrent._

// main owner: 于红雨
object MarketManagerActor extends ShardedByMarket {
  val name = "market_manager"

  def startShardRegion(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      tokenValueEstimator: TokenValueEstimator,
      ringIncomeEstimator: RingIncomeEstimator,
      dustOrderEvaluator: DustOrderEvaluator,
      tokenMetadataManager: TokenMetadataManager
    ): ActorRef = {
    numOfShards = 1
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new MarketManagerActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }

  // 如果message不包含一个有效的marketId，就不做处理，不要返回“默认值”
  val extractMarketName: PartialFunction[Any, String] = {
    case _ => ""
  }

}

class MarketManagerActor(
    extractEntityName: String => String = MarketManagerActor.extractEntityName
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val tokenValueEstimator: TokenValueEstimator,
    val ringIncomeEstimator: RingIncomeEstimator,
    val dustOrderEvaluator: DustOrderEvaluator,
    val tokenMetadataManager: TokenMetadataManager)
    extends ActorWithPathBasedConfig(MarketManagerActor.name, extractEntityName)
    with OrderRecoverSupport {
  val marketName = entityName

  val wethTokenAddress = config.getString("weth.address")

  val gasLimitPerRingV2 = BigInt(
    config.getString("loopring-protocol.gas-limit-per-ring-v2")
  )

  protected def gasPriceActor = actors.get(GasPriceActor.name)
  protected def orderbookManagerActor = actors.get(OrderbookManagerActor.name)
  protected def settlementActor = actors.get(RingSettlementActor.name)

  // TODO(yongfeng): load marketconfig from database throught a service interface
  // based on marketName
  val xorderbookConfig = XMarketConfig(
    levels = selfConfig.getInt("levels"),
    priceDecimals = selfConfig.getInt("price-decimals"),
    precisionForAmount = selfConfig.getInt("precision-for-amount"),
    precisionForTotal = selfConfig.getInt("precision-for-total")
  )

  //todo: need refactor
  val shardId = self.path.toString.split("/").last //todo：如何获取shardId

  implicit var marketId
    : XMarketId = XMarketId("token1", "token2") //todo:如何通过shardId得到marketId

  private val ringMatcher = new RingMatcherImpl()
  private val pendingRingPool = new PendingRingPoolImpl()

  implicit val aggregator = new OrderAwareOrderbookAggregatorImpl(
    selfConfig.getInt("price-decimals")
  )
  private val manager = new MarketManagerImpl(
    marketId,
    tokenMetadataManager,
    ringMatcher,
    pendingRingPool,
    dustOrderEvaluator,
    aggregator
  )
  recoverySettings = XOrderRecoverySettings(
    selfConfig.getBoolean("skip-recovery"),
    selfConfig.getInt("recover-batch-size"),
    "",
    Some(marketId)
  )
  requestOrderRecovery(recoverySettings)

  def receive: Receive = LoggingReceive {

    case XSubmitOrderReq(_, Some(xorder)) ⇒
      submitOrder(xorder)

    case XCancelOrderReq(orderId, hardCancel, _) ⇒
      manager.cancelOrder(orderId) foreach { orderbookUpdate ⇒
        orderbookManagerActor ! orderbookUpdate
      }
      sender ! XCancelOrderRes(id = orderId)

    case XGasPriceUpdated(_gasPrice) =>
      val gasPrice: BigInt = _gasPrice
      manager.triggerMatch(true, getRequiredMinimalIncome(gasPrice)) foreach {
        matchResult =>
          updateOrderbookAndSettleRings(matchResult, gasPrice)
      }

    case XTriggerRematchReq(sellOrderAsTaker, offset) =>
      for {
        res <- (gasPriceActor ? XGetGasPriceReq()).mapAs[XGetGasPriceRes]
        gasPrice: BigInt = res.gasPrice
        minRequiredIncome = getRequiredMinimalIncome(gasPrice)
        _ = manager
          .triggerMatch(sellOrderAsTaker, minRequiredIncome, offset)
          .foreach { updateOrderbookAndSettleRings(_, gasPrice) }
      } yield Unit

  }

  private def submitOrder(xorder: XOrder): Future[Unit] = {
    assert(
      xorder.actual.nonEmpty,
      "order in XSubmitOrderReq miss `actual` field"
    )
    val order: Order = xorder
    xorder.status match {
      case XOrderStatus.STATUS_NEW | XOrderStatus.STATUS_PENDING =>
        for {
          // get ring settlement cost
          res <- (gasPriceActor ? XGetGasPriceReq()).mapAs[XGetGasPriceRes]

          gasPrice: BigInt = res.gasPrice
          minRequiredIncome = getRequiredMinimalIncome(gasPrice)

          // submit order to reserve balance and allowance
          matchResult = manager.submitOrder(order, minRequiredIncome)

          //settlement matchResult and update orderbook
          _ = updateOrderbookAndSettleRings(matchResult, gasPrice)
        } yield Unit

      case s =>
        log.error(s"unexpected order status in XSubmitOrderReq: $s")
        Future.successful(Unit)
    }
  }

  private def getRequiredMinimalIncome(gasPrice: BigInt): Double = {
    val costinEth = gasLimitPerRingV2 * gasPrice
    tokenValueEstimator.getEstimatedValue(wethTokenAddress, costinEth)
  }

  private def updateOrderbookAndSettleRings(
      matchResult: MatchResult,
      gasPrice: BigInt
    ) {
    // Settle rings
    if (matchResult.rings.nonEmpty) {
      log.debug(s"rings: ${matchResult.rings}")

      settlementActor ! XSettleRingsReq(
        rings = matchResult.rings,
        gasLimit = gasLimitPerRingV2 * matchResult.rings.size,
        gasPrice = gasPrice
      )
    }

    // Update order book (depth)
    val ou = matchResult.orderbookUpdate
    if (ou.sells.nonEmpty || ou.buys.nonEmpty) {
      orderbookManagerActor ! ou
    }
  }

  protected def recoverOrder(xraworder: XRawOrder): Future[Any] =
    submitOrder(xraworder)
}
