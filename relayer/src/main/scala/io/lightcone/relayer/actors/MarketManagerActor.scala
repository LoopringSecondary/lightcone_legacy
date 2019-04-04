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

package io.lightcone.relayer.actors

import akka.actor.{Address => _, _}
import akka.pattern.ask
import akka.serialization.Serialization
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.ethereum._
import io.lightcone.ethereum.event._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import kamon.metric._
import scala.concurrent._
import scala.concurrent.duration._

// Owenr: Hongyu
object MarketManagerActor extends DeployedAsShardedByMarket {
  val name = "market_manager"

  var metadataManager: MetadataManager = _

  import MarketMetadata.Status._

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
      deployActorsIgnoringRoles: Boolean,
      dbModule: DatabaseModule
    ): ActorRef = {
    this.metadataManager = metadataManager
    startSharding(Props(new MarketManagerActor()))
  }

  import TxStatus._

  // 如果message不包含一个有效的marketPair，就不做处理，不要返回“默认值”
  //READONLY的不能在该处拦截，需要在validtor中截取，因为该处还需要将orderbook等恢复
  val extractShardingObject: PartialFunction[Any, MarketPair] = {
    case SubmitSimpleOrder(_, Some(order))
        if metadataManager.isMarketStatus(
          MarketPair(order.tokenS, order.tokenB),
          ACTIVE,
          READONLY
        ) =>
      MarketPair(order.tokenS, order.tokenB)

    case req: CancelOrder.Req
        if req.marketPair.nonEmpty && metadataManager.isMarketStatus(
          req.getMarketPair,
          ACTIVE,
          READONLY
        ) =>
      req.getMarketPair

    case req: RingMinedEvent
        if req.header.nonEmpty &&
          (req.getHeader.txStatus == TX_STATUS_SUCCESS ||
            req.getHeader.txStatus == TX_STATUS_FAILED) &&
          req.marketPair.nonEmpty &&
          req.orderIds.size > 1 =>
      req.getMarketPair

    case Notify(KeepAliveActor.NOTIFY_MSG, marketPairStr) =>
      val tokens = marketPairStr.split("-")
      MarketPair(tokens(0), tokens(1))

    case GetOrderbookSlots.Req(Some(marketPair), _) => marketPair
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
    val metadataManager: MetadataManager,
    val dbModule: DatabaseModule)
    extends InitializationRetryActor
    with ShardingEntityAware
    with RepeatedJobActor
    with BlockingReceive
    with ActorLogging {
  import ErrorCode._
  import OrderStatus._
  import MarketMetadata.Status._
  import MarketManager.MatchResult
  import TxStatus._

  val selfConfig = config.getConfig(MarketManagerActor.name)

  implicit val marketPair = {
    metadataManager
      .getMarkets(ACTIVE, READONLY)
      .find(
        m =>
          MarketManagerActor
            .getEntityId(m.metadata.get.marketPair.get) == entityId
      )
      .map(_.metadata.get.marketPair.get)
      .getOrElse {
        val error = s"unable to find market pair matching entity id ${entityId}"
        log.error(error)
        throw new IllegalStateException(error)
      }
  }

  private val metricName: String = {
    def symbol(token: String) =
      metadataManager
        .getTokenWithAddress(token)
        .getOrElse(
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"not found token: $token"
          )
        )
        .getSymbol()
    s"market_${symbol(marketPair.baseToken)}_${symbol(marketPair.quoteToken)}"
  }

  val count = KamonSupport.counter(metricName)
  val gauge = KamonSupport.gauge(metricName)
  val histo = KamonSupport.histogram(metricName)
  val timer = KamonSupport.timer(metricName)
  val recover_timer = KamonSupport.timer(metricName)

  log.info(s"===> starting MarketManagerActor ${self.path} for ${marketPair}")

  var autoSwitchBackToReady: Option[Cancellable] = None

  val wethTokenAddress = config.getString("relay.weth-address")
  val skiprecover = selfConfig.getBoolean("skip-recover")

  val maxSettementFailuresPerOrder =
    selfConfig.getInt("max-ring-failures-per-order")

  val maxRecoverDurationMinutes =
    selfConfig.getInt("max-recover-duration-minutes")

  val syncGasPriceDelayInSeconds =
    selfConfig.getInt("sync-gasprice-delay-in-seconds")

  val gasLimitPerRingV2 = BigInt(
    config.getString("loopring_protocol.gas-limit-per-ring-v2")
  )

  val ringMatcher = new RingMatcherImpl()
  val pendingRingPool = new PendingRingPoolImpl()

  def marketMetadata =
    metadataManager
      .getMarket(marketPair)
      .metadata
      .getOrElse(
        throw ErrorException(
          ERR_INTERNAL_UNKNOWN,
          s"not found metadata with marketPair:$marketPair"
        )
      )

  implicit val aggregator = new OrderbookAggregatorImpl(
    marketMetadata.priceDecimals,
    marketMetadata.precisionForAmount,
    marketMetadata.precisionForTotal
  )

  val manager = new MarketManagerImpl(
    marketPair,
    metadataManager,
    ringMatcher,
    pendingRingPool,
    dustOrderEvaluator,
    aggregator,
    maxSettementFailuresPerOrder
  )

  @inline def gasPriceActor = actors.get(GasPriceActor.name)
  @inline def settlementActor = actors.get(RingSettlementManagerActor.name)
  @inline def orderbookManagerActor = actors.get(OrderbookManagerActor.name)
  @inline def mama = actors.get(MultiAccountManagerActor.name)

  var gasPrice: BigInt = _
  var recoverTimer: Option[StartedTimer] = None

  override def initialize() = {
    recoverTimer = Some(timer.refine("label" -> "recover").start)
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
              marketPair = Some(marketPair),
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
    } yield Unit
  }

  def recover: Receive = {

    case SubmitSimpleOrder(_, Some(order)) =>
      blocking(recover_timer, "recover_submit_order") {
        count.refine("label" -> "recover_order").increment()
        submitOrder(order.copy(submittedAt = timeProvider.getTimeMillis))
          .sendTo(sender)
      }
    case msg @ ActorRecover.Finished(timeout) =>
      autoSwitchBackToReady.foreach(_.cancel)
      autoSwitchBackToReady = None
      s"market manager `${entityId}` recover completed (timeout=${timeout})"
      becomeReady()

      recoverTimer.foreach(_.stop)
      recoverTimer = None

      val numOfOrders = manager.getNumOfOrders
      gauge.refine("label" -> "num_orders").set(numOfOrders)
      histo.refine("label" -> "num_orders").record(numOfOrders)

    case msg: Any =>
      count.refine("label" -> "unhandled_msg_dur_recover").increment()
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
      blocking(timer, "submit_order") {
        submitOrder(order).sendTo(sender).andThen {
          case _ =>
            val numOfOrders = manager.getNumOfOrders
            gauge.refine("label" -> "num_orders").set(numOfOrders)
            histo.refine("label" -> "num_orders").record(numOfOrders)
            count.refine("label" -> "submit_order").increment()
        }
      }

    case req: CancelOrder.Req =>
      val t = timer.refine("label" -> "cancel_order").start()

      manager.cancelOrder(req.id) foreach { orderbookUpdate =>
        orderbookManagerActor ! orderbookUpdate.copy(
          marketPair = Some(marketPair)
        )
      }
      sender ! CancelOrder.Res(status = req.status)

      t.stop()
      val numOfOrders = manager.getNumOfOrders
      gauge.refine("label" -> "num_orders").set(numOfOrders)
      histo.refine("label" -> "num_orders").record(numOfOrders)
      count.refine("label" -> "cancel_order").increment()

    case GasPriceUpdatedEvent(_gasPrice) =>
      val t = timer.refine("label" -> "rematch").start()

      this.gasPrice = _gasPrice
      manager.triggerMatch(true, getRequiredMinimalIncome()) foreach {
        matchResult =>
          updateOrderbookAndSettleRings(matchResult)
      }

      t.stop()
      val numOfOrders = manager.getNumOfOrders
      gauge.refine("label" -> "num_orders").set(numOfOrders)
      histo.refine("label" -> "num_orders").record(numOfOrders)
      count.refine("label" -> "rematch").increment()

    case TriggerRematch(sellOrderAsTaker, offset) =>
      val t = timer.refine("label" -> "rematch").start()

      manager
        .triggerMatch(sellOrderAsTaker, getRequiredMinimalIncome(), offset)
        .foreach { updateOrderbookAndSettleRings(_) }

      t.stop()
      val numOfOrders = manager.getNumOfOrders
      gauge.refine("label" -> "num_orders").set(numOfOrders)
      histo.refine("label" -> "num_orders").record(numOfOrders)
      count.refine("label" -> "rematch").increment()

    case req @ RingMinedEvent(Some(header), orderIds, _) =>
      if ((header.txStatus != TX_STATUS_SUCCESS &&
          header.txStatus != TX_STATUS_FAILED) && orderIds.size <= 1) {
        log.error(s"unexpected msg : $req")
      } else {
        blocking(timer, "handle_ring_mind_event") {
          log.debug(s"MarketManagerActor -- receive RingMinedEvent:${req}")
          Future {
            val ringhash =
              createRingIdByOrderHash(orderIds(0), orderIds(1))

            val result = if (header.txStatus == TxStatus.TX_STATUS_SUCCESS) {
              manager.deleteRing(ringhash, true)
            } else if (header.txStatus == TxStatus.TX_STATUS_FAILED) {
              val matchResults = manager.deleteRing(ringhash, false)
              if (matchResults.nonEmpty) {
                matchResults.foreach { matchResult =>
                  updateOrderbookAndSettleRings(matchResult)
                }
              }
            }

            val numOfOrders = manager.getNumOfOrders
            gauge.refine("label" -> "num_orders").set(numOfOrders)
            histo.refine("label" -> "num_orders").record(numOfOrders)
            count.refine("label" -> "ring_mined_evnet").increment()

          } sendTo sender
        }
      }

    case req: MetadataChanged =>
      val metadataOpt = try {
        Option(metadataManager.getMarket(marketPair).getMetadata)
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
          count.refine("label" -> "metadata_updated").increment()
          log.info(s"metadata changed: $metadata")

      }

    case req: GetOrderbookSlots.Req =>
      count.refine("label" -> "get_orderbook").increment()

      sender ! GetOrderbookSlots.Res(
        Some(manager.getOrderbookSlots(req.numOfSlots))
      )

  }

  private def submitOrder(order: Order): Future[MatchResult] = Future {
    log.debug(s"marketmanager.submitOrder ${order}")
    val matchable: Matchable = order
    order.status match {
      case STATUS_NEW | STATUS_PENDING =>
        //不再通过该处返回错误，而是通过match时，判断订单的状态
//        if (order.actual.isEmpty) {
//          println("##### order.actual.isEmpty ")
//          throw ErrorException(
//            ErrorCode.ERR_INVALID_ORDER_DATA,
//            "order in SubmitSimpleOrder miss `actual` field"
//          )
//        }
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

  private def updateOrderbookAndSettleRings(matchResult: MatchResult): Unit = {
    // Settle rings
    log.debug(
      s"MarketManagerActor -- updateOrderbookAndSettleRings -- matchResult:${matchResult}"
    )
    if (matchResult.rings.nonEmpty) {
      settlementActor ! SettleRings(
        rings = matchResult.rings,
        gasPrice = gasPrice
      )
    }

    // Update order book (depth)
    val ou = matchResult.orderbookUpdate

    if (ou.sells.nonEmpty || ou.buys.nonEmpty) {
      orderbookManagerActor ! ou.copy(marketPair = Some(marketPair))
    }

    //该处还有保留的价值的，因为还可能是由于定时撮合等引起的，所以不能依赖于AccountManagerActor根据MatchResult的处理
    if (matchResult.taker.status == STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES) {
      for {
        takerOpt <- dbModule.orderService.getOrder(matchResult.taker.id)
      } yield {
        takerOpt.foreach { taker =>
          mama ! CancelOrder.Req(
            id = taker.hash,
            owner = taker.owner,
            status = matchResult.taker.status,
            marketPair = Some(MarketPair(taker.tokenS, taker.tokenB))
          )
        }
      }
    }
  }

  def syncGasPrice(): Future[Unit] =
    for {
      res <- (gasPriceActor ? GetGasPrice.Req())
        .mapAs[GetGasPrice.Res]
    } yield this.gasPrice = res.gasPrice

  val repeatedJobs = Seq(
    Job(
      name = "sync-gasprice",
      dalayInSeconds = syncGasPriceDelayInSeconds, // 10 minutes
      run = () => syncGasPrice()
    )
  )

}
