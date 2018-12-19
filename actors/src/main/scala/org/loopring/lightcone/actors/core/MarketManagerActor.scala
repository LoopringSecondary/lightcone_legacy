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
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core.market.MarketManager.MatchResult
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.proto._

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.duration._

// main owner: 于红雨
object MarketManagerActor extends ShardedByMarket {
  val name = "market_manager"

  def startShardRegion()(
    implicit system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef],
    tokenValueEstimator: TokenValueEstimator,
    ringIncomeEstimator: RingIncomeEstimator,
    dustOrderEvaluator: DustOrderEvaluator,
    tokenMetadataManager: TokenMetadataManager): ActorRef = {
    numOfShards = 10

    val markets = config
      .getObjectList("markets")
      .asScala
      .flatMap { item =>
        val c = item.toConfig
        val marketId =
          XMarketId(c.getString("priamry"), c.getString("secondary"))
        val hashOpt = MarketManagerActor
          .hashed(Some(marketId))
        hashOpt map (_.toString -> marketId)
      }
      .toMap

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new MarketManagerActor(markets)),
      settings = ClusterShardingSettings(system).withRole(name),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId)
  }

  // 如果message不包含一个有效的marketId，就不做处理，不要返回“默认值”
  val extractMarketId: PartialFunction[Any, XMarketId] = {
    case XSubmitSimpleOrderReq(_, Some(xorder)) =>
      XMarketId(xorder.tokenS, xorder.tokenB)
    case XCancelOrderReq(_, _, _, Some(marketId)) =>
      marketId
  }

}

class MarketManagerActor(
  markets: Map[String, XMarketId])(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val tokenValueEstimator: TokenValueEstimator,
    val ringIncomeEstimator: RingIncomeEstimator,
    val dustOrderEvaluator: DustOrderEvaluator,
    val tokenMetadataManager: TokenMetadataManager)
  extends ActorWithPathBasedConfig(
    MarketManagerActor.name,
    MarketManagerActor.extractEntityName)
  with ActorLogging {

  var autoSwitchBackToReceive: Option[Cancellable] = None

  val wethTokenAddress = config.getString("weth.address")
  val skiprecover = selfConfig.getBoolean("skip-recover")

  val maxRecoverDurationMinutes =
    selfConfig.getInt("max-recover-duration-minutes")

  val gasLimitPerRingV2 = BigInt(
    config.getString("loopring-protocol.gas-limit-per-ring-v2"))

  val ringMatcher = new RingMatcherImpl()
  val pendingRingPool = new PendingRingPoolImpl()

  implicit val marketId = markets(entityName)

  implicit val aggregator = new OrderAwareOrderbookAggregatorImpl(
    selfConfig.getInt("price-decimals"))

  val manager = new MarketManagerImpl(
    marketId,
    tokenMetadataManager,
    ringMatcher,
    pendingRingPool,
    dustOrderEvaluator,
    aggregator)

  protected def gasPriceActor = actors.get(GasPriceActor.name)
  protected def orderbookManagerActor = actors.get(OrderbookManagerActor.name)
  protected def settlementActor = actors.get(RingSettlementActor.name)

  override def preStart(): Unit = {
    super.preStart()

    autoSwitchBackToReceive = Some(
      context.system.scheduler
        .scheduleOnce(maxRecoverDurationMinutes.minute, self, XRecoverEnded(true)))

    if (skiprecover) {
      log.warning(s"actor recover skipped: ${self.path}")
    } else {
      context.become(recover)
      log.debug(s"actor recover started: ${self.path}")
      actors.get(OrderRecoverCoordinator.name) !
        XRecoverReq(marketIds = Seq(marketId))
    }
  }

  def recover: Receive = {

    case XSubmitSimpleOrderReq(_, Some(xorder)) ⇒
      submitOrder(xorder)

    case msg @ XRecoverEnded(timeout) =>
      autoSwitchBackToReceive.foreach(_.cancel)
      autoSwitchBackToReceive = None
      s"market manager `${entityName}` recover completed (due to timeout: ${timeout})"
      context.become(receive)

    case msg: Any =>
      log.warning(s"message not handled during recover")
      sender ! XError(
        ERR_REJECTED_DURING_RECOVER,
        s"market manager `${entityName}` is being recovered")
  }

  def receive: Receive = {

    case XSubmitSimpleOrderReq(_, Some(xorder)) ⇒
      submitOrder(xorder)

    case XCancelOrderReq(orderId, _, _, _) ⇒
      manager.cancelOrder(orderId) foreach { orderbookUpdate ⇒
        orderbookManagerActor ! orderbookUpdate.copy(marketId = Some(marketId))
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
      "order in XSubmitSimpleOrderReq miss `actual` field")
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
        log.error(s"unexpected order status in XSubmitSimpleOrderReq: $s")
        Future.successful(Unit)
    }
  }

  private def getRequiredMinimalIncome(gasPrice: BigInt): Double = {
    val costinEth = gasLimitPerRingV2 * gasPrice
    tokenValueEstimator.getEstimatedValue(wethTokenAddress, costinEth)
  }

  private def updateOrderbookAndSettleRings(
    matchResult: MatchResult,
    gasPrice: BigInt) {
    // Settle rings
    if (matchResult.rings.nonEmpty) {
      log.debug(s"rings: ${matchResult.rings}")

      settlementActor ! XSettleRingsReq(
        rings = matchResult.rings,
        gasLimit = gasLimitPerRingV2 * matchResult.rings.size,
        gasPrice = gasPrice)
    }

    // Update order book (depth)
    val ou = matchResult.orderbookUpdate
    if (ou.sells.nonEmpty || ou.buys.nonEmpty) {
      orderbookManagerActor ! ou.copy(marketId = Some(marketId))
    }
  }

  def recoverOrder(xraworder: XRawOrder): Future[Any] =
    submitOrder(xraworder)

}
