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
import org.loopring.lightcone.actors.core.OrderbookManagerActor.getEntityId
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.utils.MetadataRefresher
import org.loopring.lightcone.lib.data._
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

  var metadataManager: MetadataManager = _

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
      metadataManager: MetadataManager,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    this.metadataManager = metadataManager

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new MarketManagerActor()),
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }

  // 如果message不包含一个有效的marketId，就不做处理，不要返回“默认值”
  //READONLY的不能在该处拦截，需要在validtor中截取，因为该处还需要将orderbook等恢复
  val extractMarketId: PartialFunction[Any, MarketId] = {
    case SubmitSimpleOrder(_, Some(order))
        if metadataManager.isValidMarket(
          MarketId(order.tokenS, order.tokenB)
        ) =>
      MarketId(order.tokenS, order.tokenB)

    case CancelOrder.Req(_, _, _, Some(marketId))
        if metadataManager.isValidMarket(marketId) =>
      marketId

    case req: RingMinedEvent if req.fills.size >= 2 =>
      MarketId(req.fills(0).tokenS, req.fills(1).tokenS)

    case Notify(KeepAliveActor.NOTIFY_MSG, marketIdStr) =>
      val tokens = marketIdStr.split("-")
      MarketId(tokens(0), tokens(1))

    case GetOrderbookSlots.Req(Some(marketId), _) => marketId
  }

}

// TODO:撮合应该有个暂停撮合提交的逻辑，适用于：区块落后太多、没有可用的RingSettlement等情况
class MarketManagerActor(
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
    val metadataManager: MetadataManager)
    extends ActorWithPathBasedConfig(
      MarketManagerActor.name,
      MarketManagerActor.extractEntityId
    )
    with RepeatedJobActor
    with ActorLogging {
  implicit val marketId: MarketId = metadataManager.getValidMarketIds.values
    .find(m => getEntityId(m) == entityId)
    .get

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

  def marketMetadata = metadataManager.getMarketMetadata(marketId)

  implicit val aggregator = new OrderAwareOrderbookAggregatorImpl(
    marketMetadata.priceDecimals,
    marketMetadata.precisionForAmount,
    marketMetadata.precisionForTotal
  )

  val manager = new MarketManagerImpl(
    marketId,
    metadataManager,
    ringMatcher,
    pendingRingPool,
    dustOrderEvaluator,
    aggregator,
    maxSettementFailuresPerOrder
  )

  protected def gasPriceActor = actors.get(GasPriceActor.name)
  protected def settlementActor = actors.get(RingSettlementManagerActor.name)
  protected def metadataRefresher = actors.get(MetadataRefresher.name)
  protected def orderbookManagerActor = actors.get(OrderbookManagerActor.name)

  var gasPrice: BigInt = _

  override def initialize() =
    for {
      _ <- syncGasPrice()
      _ <- if (skiprecover) Future.successful {
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
      _ = metadataRefresher ! SubscribeMetadataChanged()
    } yield Unit

  def recover: Receive = {

    case SubmitSimpleOrder(_, Some(order)) =>
      submitOrder(order.copy(submittedAt = timeProvider.getTimeMillis))

    case msg @ ActorRecover.Finished(timeout) =>
      autoSwitchBackToReady.foreach(_.cancel)
      autoSwitchBackToReady = None
      s"market manager `${entityId}` recover completed (timeout=${timeout})"
      becomeReady()

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
    case req @ Notify(KeepAliveActor.NOTIFY_MSG, _) =>
      sender ! req

    case SubmitSimpleOrder(_, Some(order)) =>
      submitOrder(order).sendTo(sender)

    case CancelOrder.Req(orderId, _, _, _) =>
      manager.cancelOrder(orderId) foreach { orderbookUpdate =>
        orderbookManagerActor ! orderbookUpdate.copy(marketId = Some(marketId))
      }
      sender ! CancelOrder.Res(id = orderId)

    case GasPriceUpdated(_gasPrice) =>
      this.gasPrice = _gasPrice
      manager.triggerMatch(true, getRequiredMinimalIncome()) foreach {
        matchResult =>
          updateOrderbookAndSettleRings(matchResult)
      }

    case TriggerRematch(sellOrderAsTaker, offset) =>
      manager
        .triggerMatch(sellOrderAsTaker, getRequiredMinimalIncome(), offset)
        .foreach { updateOrderbookAndSettleRings(_) }

    case RingMinedEvent(Some(header), _, _, _, fills) =>
      Future {
        val ringhash =
          createRingIdByOrderHash(fills(0).orderHash, fills(1).orderHash)
        if (header.txStatus == TxStatus.TX_STATUS_SUCCESS) {
          manager.deleteRing(ringhash, true)
        } else if (header.txStatus == TxStatus.TX_STATUS_FAILED) {
          val matchResults = manager.deleteRing(ringhash, false)
          if (matchResults.nonEmpty) {
            matchResults.foreach { matchResult =>
              updateOrderbookAndSettleRings(matchResult)
            }
          }
        }
      } sendTo sender

    case req: MetadataChanged =>
      val metadataOpt = try {
        Option(metadataManager.getMarketMetadata(marketId))
      } catch {
        case _: Throwable => None
      }
      metadataOpt match {
        case None =>
          log.warning("I'm stopping myself as the market metadata is not found")
          context.system.stop(self)

        case Some(metadata) if metadata.status.isTerminated =>
          log.warning(
            s"I'm stopping myself as the market is terminiated: $metadata"
          )
          context.system.stop(self)

        case Some(metadata) =>
          log.info(s"metadata changed: $metadata")
      }

    case req: GetOrderbookSlots.Req =>
      sender ! GetOrderbookSlots.Res(
        Some(manager.getOrderbookSlots(req.numOfSlots))
      )
  }

  private def submitOrder(order: Order): Future[MatchResult] = Future {
    log.debug(s"marketmanager.submitOrder ${order}")
    val matchable: Matchable = order
    order.status match {
      case OrderStatus.STATUS_NEW | OrderStatus.STATUS_PENDING |
          OrderStatus.STATUS_PARTIALLY_FILLED =>
        if (order.actual.isEmpty) {
          throw ErrorException(
            ErrorCode.ERR_INVALID_ORDER_DATA,
            "order in SubmitSimpleOrder miss `actual` field"
          )
        }
        // submit order to reserve balance and allowance
        val matchResult =
          manager.submitOrder(matchable, getRequiredMinimalIncome())

        log.debug(s"matchResult, ${matchResult}")
        //settlement matchResult and update orderbook
        updateOrderbookAndSettleRings(matchResult)
        matchResult

      case s =>
        log.error(s"unexpected order status in SubmitSimpleOrder: $s")
        throw ErrorException(
          ErrorCode.ERR_INVALID_ORDER_DATA,
          s"unexpected order status in SubmitSimpleOrder: $s"
        )
    }
  }

  private def getRequiredMinimalIncome(): Double = {
    val costinEth = gasLimitPerRingV2 * gasPrice
    tve.getValue(wethTokenAddress, costinEth)
  }

  private def updateOrderbookAndSettleRings(matchResult: MatchResult) {
    // Settle rings
    if (matchResult.rings.nonEmpty) {
      settlementActor ! SettleRings(
        rings = matchResult.rings,
        gasPrice = gasPrice
      )
    }

    // Update order book (depth)
    val ou = matchResult.orderbookUpdate

    if (ou.sells.nonEmpty || ou.buys.nonEmpty) {
      orderbookManagerActor ! ou.copy(marketId = Some(marketId))
    }
  }

  def recoverOrder(xraworder: RawOrder): Future[Any] =
    submitOrder(xraworder)

  def syncGasPrice(): Future[Unit] =
    for {
      res <- (gasPriceActor ? GetGasPrice.Req())
        .mapAs[GetGasPrice.Res]
    } yield this.gasPrice = res.gasPrice

  val repeatedJobs = Seq(
    Job(
      name = "sync-gasprice",
      dalayInSeconds = 60, // 10 minutes
      initialDalayInSeconds = 10,
      run = () => syncGasPrice()
    )
  )

}
