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
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.cluster.sharding._
import akka.pattern.ask
import akka.serialization.Serialization
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core.market.MarketManager.MatchResult
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.ethereum.data.{Address => LAddress}
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.duration._

// Owenr: Hongyu
object MarketManagerActor extends ShardedByMarket {
  val name = "market_manager"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      tve: TokenValueEvaluator,
      rie: RingIncomeEvaluator,
      dustOrderEvaluator: DustOrderEvaluator,
      tokenManager: TokenManager,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {

    val markets = config
      .getObjectList("markets")
      .asScala
      .map { item =>
        val c = item.toConfig
        val marketId =
          MarketId(
            LAddress(c.getString("priamry")).toString,
            LAddress(c.getString("secondary")).toString
          )
        MarketManagerActor.getEntityId(marketId) -> marketId
      }
      .toMap

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new MarketManagerActor(markets)),
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }

  // 如果message不包含一个有效的marketId，就不做处理，不要返回“默认值”
  val extractMarketId: PartialFunction[Any, MarketId] = {
    case SubmitSimpleOrder(_, Some(order)) =>
      MarketId(order.tokenS, order.tokenB)
    case CancelOrder.Req(_, _, _, Some(marketId)) =>
      marketId
    case req: RingMinedEvent if req.fills.size >= 2 =>
      MarketId(req.fills(0).tokenS, req.fills(1).tokenS)
    case Notify(AliveKeeperActor.NOTIFY_MSG, marketIdStr) =>
      val tokens = marketIdStr.split("-")
      val (primary, secondary) = (tokens(0), tokens(1))
      MarketId(primary, secondary)
  }

}

//todo:撮合应该有个暂停撮合提交的逻辑，适用于：区块落后太多、没有可用的RingSettlement等情况
class MarketManagerActor(
    markets: Map[String, MarketId]
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val tve: TokenValueEvaluator,
    val rie: RingIncomeEvaluator,
    val dustOrderEvaluator: DustOrderEvaluator,
    val tokenManager: TokenManager)
    extends ActorWithPathBasedConfig(
      MarketManagerActor.name,
      MarketManagerActor.extractEntityId
    )
    with ActorLogging {

  implicit val marketId = markets(entityId)
  log.info(s"=======> starting MarketManagerActor ${self.path} for ${marketId}")

  var autoSwitchBackToReady: Option[Cancellable] = None

  val wethTokenAddress = config.getString("relay.weth-address")
  val skiprecover = selfConfig.getBoolean("skip-recover")

  val maxSettementFailuresPerOrder =
    selfConfig.getInt("max-ring-failures-per-order")

  val maxRecoverDurationMinutes =
    selfConfig.getInt("max-recover-duration-minutes")

  val gasLimitPerRingV2 = BigInt(
    config.getString("loopring_protocol.gas-limit-per-ring-v2")
  )

  val ringMatcher = new RingMatcherImpl()
  val pendingRingPool = new PendingRingPoolImpl()

  implicit val aggregator = new OrderAwareOrderbookAggregatorImpl(
    selfConfig.getInt("price-decimals"),
    selfConfig.getInt("precision-for-amount"),
    selfConfig.getInt("precision-for-total")
  )

  val manager = new MarketManagerImpl(
    marketId,
    tokenManager,
    ringMatcher,
    pendingRingPool,
    dustOrderEvaluator,
    aggregator,
    maxSettementFailuresPerOrder
  )

  protected def gasPriceActor = actors.get(GasPriceActor.name)
  protected def orderbookManagerMediator =
    DistributedPubSub(context.system).mediator
  protected def settlementActor = actors.get(RingSettlementManagerActor.name)

  override def initialize() = {
    if (skiprecover) Future.successful {
      log.debug(s"actor recover skipped: ${self.path}")
      becomeReady()
    } else {
      log.debug(s"actor recover started: ${self.path}")
      context.become(recover)
      for {
        _ <- actors.get(OrderRecoverCoordinator.name) ?
          ActorRecover.Request(
            marketId = Some(marketId),
            sender = Serialization.serializedActorPath(self)
          )
      } yield {
        autoSwitchBackToReady = Some(
          context.system.scheduler
            .scheduleOnce(
              maxRecoverDurationMinutes.minute,
              self,
              ActorRecover.Finished(true)
            )
        )
      }
    }
  }

  def recover: Receive = {

    case SubmitSimpleOrder(_, Some(order)) =>
      submitOrder(order.copy(submittedAt = timeProvider.getTimeMillis))

    case msg @ ActorRecover.Finished(timeout) =>
      autoSwitchBackToReady.foreach(_.cancel)
      autoSwitchBackToReady = None
      s"market manager `${entityId}` recover completed (timeout=${timeout})"
      context.become(ready)

    case msg: Any =>
      log.warning(s"message not handled during recover, ${msg}, ${sender}")
      //sender 是自己时，不再发送Error信息
      if (sender != self) {
        sender ! Error(
          ERR_REJECTED_DURING_RECOVER,
          s"market manager `${entityId}` is being recovered"
        )
      }
  }

  def ready: Receive = {

    case SubmitSimpleOrder(_, Some(order)) =>
      submitOrder(order).sendTo(sender)

    case CancelOrder.Req(orderId, _, _, _) =>
      manager.cancelOrder(orderId) foreach { orderbookUpdate =>
        orderbookManagerMediator ! Publish(
          OrderbookManagerActor.getTopicId(marketId),
          orderbookUpdate.copy(marketId = Some(marketId))
        )
      }
      sender ! CancelOrder.Res(id = orderId)

    case GasPriceUpdated(_gasPrice) =>
      val gasPrice: BigInt = _gasPrice
      manager.triggerMatch(true, getRequiredMinimalIncome(gasPrice)) foreach {
        matchResult =>
          updateOrderbookAndSettleRings(matchResult, gasPrice)
      }

    case TriggerRematch(sellOrderAsTaker, offset) =>
      for {
        res <- (gasPriceActor ? GetGasPrice.Req()).mapAs[GetGasPrice.Res]
        gasPrice: BigInt = res.gasPrice
        minRequiredIncome = getRequiredMinimalIncome(gasPrice)
        _ = manager
          .triggerMatch(sellOrderAsTaker, minRequiredIncome, offset)
          .foreach { updateOrderbookAndSettleRings(_, gasPrice) }
      } yield Unit

    case RingMinedEvent(Some(header), _, _, _, fills) =>
      Future {
        val ringhash =
          createRingIdByOrderHash(fills(0).orderHash, fills(1).orderHash)
        if (header.txStatus == TxStatus.TX_STATUS_SUCCESS) {
          manager.deleteRing(ringhash, true)
        } else if (header.txStatus == TxStatus.TX_STATUS_FAILED) {
          val matchResults = manager.deleteRing(ringhash, false)
          if (matchResults.nonEmpty) {
            for {
              res <- (gasPriceActor ? GetGasPrice.Req()).mapAs[GetGasPrice.Res]
              gasPrice: BigInt = res.gasPrice
              _ = matchResults.map { matchResult =>
                updateOrderbookAndSettleRings(matchResult, gasPrice)
              }

            } yield Unit
          }
        }
      } sendTo sender
  }

  private def submitOrder(order: Order): Future[Unit] = Future {
    assert(
      order.actual.nonEmpty,
      "order in SubmitSimpleOrder miss `actual` field"
    )
    val matchable: Matchable = order
    order.status match {
      case OrderStatus.STATUS_NEW | OrderStatus.STATUS_PENDING =>
        for {
          // get ring settlement cost
          res <- (gasPriceActor ? GetGasPrice.Req()).mapAs[GetGasPrice.Res]

          gasPrice: BigInt = res.gasPrice
          minRequiredIncome = getRequiredMinimalIncome(gasPrice)

          // submit order to reserve balance and allowance
          matchResult = manager.submitOrder(matchable, minRequiredIncome)
          //settlement matchResult and update orderbook
          _ = updateOrderbookAndSettleRings(matchResult, gasPrice)
        } yield Unit

      case s =>
        log.error(s"unexpected order status in SubmitSimpleOrder: $s")
    }
  }

  private def getRequiredMinimalIncome(gasPrice: BigInt): Double = {
    val costinEth = gasLimitPerRingV2 * gasPrice
    tve.getValue(wethTokenAddress, costinEth)
  }

  private def updateOrderbookAndSettleRings(
      matchResult: MatchResult,
      gasPrice: BigInt
    ) {
    // Settle rings
    if (matchResult.rings.nonEmpty) {
      log.debug(s"rings: ${matchResult.rings}")

      settlementActor ! SettleRings(
        rings = matchResult.rings,
        gasLimit = gasLimitPerRingV2 * matchResult.rings.size,
        gasPrice = gasPrice
      )
    }

    // Update order book (depth)
    val ou = matchResult.orderbookUpdate
    if (ou.sells.nonEmpty || ou.buys.nonEmpty) {
      orderbookManagerMediator ! Publish(
        OrderbookManagerActor.getTopicId(marketId),
        ou.copy(marketId = Some(marketId))
      )
    }
  }

  def recoverOrder(xraworder: RawOrder): Future[Any] =
    submitOrder(xraworder)

}
