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
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core.market.MarketManager.MatchResult
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import scala.concurrent._

// main owner: 于红雨
object MarketManagerActor {
  val name = "market_manager"

  def extractEntityName(actorName: String) = actorName.split("_").last
  private def getShardId(msg: Any) = "singleton_shard"
  private def getEntitityId(msg: Any) = "${name}_${getMarketId(msg)}"

  protected val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg ⇒ (getEntitityId(msg), msg)
  }

  protected val extractShardId: ShardRegion.ExtractShardId = {
    case msg ⇒ getShardId(msg)
  }

  def startShardRegion()(
    implicit
    system: ActorSystem,
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
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new MarketManagerActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }

  private def getMarketId(msg: Any): String = ???
}

class MarketManagerActor()(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val tokenValueEstimator: TokenValueEstimator,
    val ringIncomeEstimator: RingIncomeEstimator,
    val dustOrderEvaluator: DustOrderEvaluator,
    val tokenMetadataManager: TokenMetadataManager
) extends ActorWithPathBasedConfig(
  MarketManagerActor.name,
  MarketManagerActor.extractEntityName
) with OrderRecoverSupport {

  val wethTokenAddress = config.getString("weth.address")
  val gasLimitPerRingV2 = BigInt(config.getString("loopring-protocol.gas-limit-per-ring-v2"))

  private var marketId: XMarketId = _

  private val ringMatcher = new RingMatcherImpl()
  private val pendingRingPool = new PendingRingPoolImpl()

  private var manager: MarketManager = _

  protected def gasPriceActor = actors.get(GasPriceActor.name)
  protected def orderbookManagerActor = actors.get(OrderbookManagerActor.name)
  protected def settlementActor = actors.get(RingSettlementActor.name)

  def receive: Receive = {
    case XStart(shardEntityId) ⇒ {
      val tokens = shardEntityId.split("-")
      marketId = XMarketId(tokens(0), tokens(1))
      implicit val marketId_ = marketId
      implicit val aggregator = new OrderAwareOrderbookAggregatorImpl(
        selfConfig.getInt("price-decimals")
      )
      manager = new MarketManagerImpl(
        marketId,
        tokenMetadataManager,
        ringMatcher,
        pendingRingPool,
        dustOrderEvaluator,
        aggregator
      )
      val recoverySettings = XOrderRecoverySettings(
        selfConfig.getBoolean("skip-recovery"),
        selfConfig.getInt("recover-batch-size"),
        "",
        Some(marketId)
      )
      startOrderRecovery(recoverySettings)
    }
  }

  def functional: Receive = LoggingReceive {

    case XSubmitOrderReq(Some(xorder)) ⇒
      submitOrder(xorder)

    case XCancelOrderReq(orderId, hardCancel) ⇒
      manager.cancelOrder(orderId) foreach {
        orderbookUpdate ⇒ orderbookManagerActor ! orderbookUpdate
      }
      sender ! XCancelOrderRes(id = orderId)

    case XGasPriceUpdated(_gasPrice) ⇒
      val gasPrice: BigInt = _gasPrice
      manager.triggerMatch(true, getRequiredMinimalIncome(gasPrice)) foreach {
        matchResult ⇒
          updateOrderbookAndSettleRings(matchResult, gasPrice)
      }

    case XTriggerRematchReq(sellOrderAsTaker, offset) ⇒ for {
      res ← (gasPriceActor ? XGetGasPriceReq()).mapTo[XGetGasPriceRes]
      gasPrice: BigInt = res.gasPrice
      minRequiredIncome = getRequiredMinimalIncome(gasPrice)
      _ = manager.triggerMatch(sellOrderAsTaker, minRequiredIncome, offset)
        .foreach { updateOrderbookAndSettleRings(_, gasPrice) }
    } yield Unit

  }

  private def submitOrder(xorder: XOrder): Future[Unit] = {
    assert(xorder.actual.nonEmpty, "order in XSubmitOrderReq miss `actual` field")
    val order: Order = xorder
    xorder.status match {
      case XOrderStatus.STATUS_NEW | XOrderStatus.STATUS_PENDING ⇒ for {
        // get ring settlement cost
        res ← (gasPriceActor ? XGetGasPriceReq()).mapTo[XGetGasPriceRes]

        gasPrice: BigInt = res.gasPrice
        minRequiredIncome = getRequiredMinimalIncome(gasPrice)

        // submit order to reserve balance and allowance
        matchResult = manager.submitOrder(order, minRequiredIncome)

        //settlement matchResult and update orderbook
        _ = updateOrderbookAndSettleRings(matchResult, gasPrice)
      } yield Unit

      case s ⇒
        log.error(s"unexpected order status in XSubmitOrderReq: $s")
        Future.successful(Unit)
    }
  }

  private def getRequiredMinimalIncome(gasPrice: BigInt): Double = {
    val costinEth = gasLimitPerRingV2 * gasPrice
    tokenValueEstimator.getEstimatedValue(wethTokenAddress, costinEth)
  }

  private def updateOrderbookAndSettleRings(matchResult: MatchResult, gasPrice: BigInt) {
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

  protected def recoverOrder(xorder: XOrder): Future[Any] = submitOrder(xorder)
}
