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

import akka.actor.SupervisorStrategy._
import akka.actor._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.google.protobuf.ByteString
import com.typesafe.config.Config
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer.data._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// Owner: Hongyu
// TODO:如果刷新时间太长，或者读取次数超过一个值，就重新从以太坊读取balance/allowance，并reset这个时间和读取次数。
class AccountManagerAltActor(
    val owner: String
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dustEvaluator: DustOrderEvaluator,
    val dbModule: DatabaseModule,
    val metadataManager: MetadataManager,
    val baProvider: BalanceAndAllowanceProvider)
    extends Actor
    with AccountManagerUpdatedOrdersProcessor
    with Stash
    with BlockingReceive
    with ActorLogging {

  import ErrorCode._
  import OrderStatus._
  import TxStatus._

  implicit val orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
  implicit val uoProcessor: UpdatedOrdersProcessor = this

  val manager = AccountManagerAlt.default(owner)
  val accountCutoffState = new AccountCutoffStateImpl()

  def ethereumQueryActor = actors.get(EthereumQueryActor.name)
  def marketManagerActor = actors.get(MarketManagerActor.name)
  def orderPersistenceActor = actors.get(OrderPersistenceActor.name)

  override def preStart() = {
    self ! Notify("initialize")
  }

  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 second) {
      case e: Exception =>
        log.error(e.getMessage)
        Escalate
    }

  def receive: Receive = initialReceive

  def initialReceive: Receive = {
    case Notify("initialize", _) =>
      val batchCutoffReq =
        BatchGetCutoffs.Req((metadataManager.getValidMarketPairs map {
          case (marketHash, marketPair) =>
            GetCutoff
              .Req(broker = owner, owner = owner, marketHash = marketHash)
        }).toSeq :+ GetCutoff.Req(broker = owner, owner = owner))

      val syncCutoff = for {
        res <- (ethereumQueryActor ? batchCutoffReq).mapAs[BatchGetCutoffs.Res]
      } yield {
        res.resps foreach { cutoffRes =>
          val cutoff: BigInt = cutoffRes.cutoff
          if (cutoffRes.marketHash.isEmpty) {
            accountCutoffState.setCutoff(cutoff.toLong)
          } else {
            accountCutoffState.setTradingPairCutoff(
              cutoffRes.marketHash,
              cutoff.toLong
            )
          }
        }
      }

      syncCutoff onComplete {
        case Success(_) =>
          self ! Notify("initialized")
        case Failure(e) =>
          throw e
      }
    case Notify("initialized", _) =>
      context.become(ready)
      unstashAll()
    case _ =>
      stash()
  }

  def ready: Receive = LoggingReceive {
    case req @ Notify(KeepAliveActor.NOTIFY_MSG, _) =>
      sender ! req

    case ActorRecover.RecoverOrderReq(Some(raworder)) =>
      //恢复时，如果订单已被取消，需要更新数据库状态
      blocking {
        val marketHash = MarketPair(raworder.tokenS, raworder.tokenB).hashString
        (for {
          canceledStatus <- isOrderCanceled(raworder)
          status = if (canceledStatus) {
            STATUS_ONCHAIN_CANCELLED_BY_USER
          } else if (accountCutoffState
                       .isOrderCutoffByOwner(raworder.validSince)) {
            STATUS_ONCHAIN_CANCELLED_BY_USER
          } else if (accountCutoffState.isOrderCutoffByTradingPair(
                       marketHash,
                       raworder.validSince
                     )) {
            STATUS_ONCHAIN_CANCELLED_BY_USER_TRADING_PAIR
          } else {
            STATUS_PENDING
          }
          res <- status match {
            case STATUS_ONCHAIN_CANCELLED_BY_USER |
                STATUS_ONCHAIN_CANCELLED_BY_USER_TRADING_PAIR =>
              marketManagerActor ! CancelOrder.Req(
                id = raworder.hash,
                marketPair = Some(MarketPair(raworder.tokenS, raworder.tokenB))
              )
              dbModule.orderService
                .updateOrderStatus(raworder.hash, status)
                .map { _ =>
                  ActorRecover.OrderRecoverResult(raworder.hash, true)
                }
            case _ =>
              resubmitOrder(raworder).map { _ =>
                ActorRecover.OrderRecoverResult(raworder.hash, true)
              }
          }
        } yield res).sendTo(sender)
      }

    case GetBalanceAndAllowances.Req(addr, tokens, _) =>
      blocking {
        (for {
          accountInfos <- Future.sequence(tokens.map(manager.getAccountInfo))
          _ = assert(tokens.size == accountInfos.size)
          balanceAndAllowanceMap = accountInfos.map { i =>
            i.token -> i
          }.toMap.map {
            case (token, ai) =>
              token -> BalanceAndAllowance(
                ai.balance,
                ai.allowance,
                ai.availableBalance,
                ai.availableAllowance
              )
          }
          result = GetBalanceAndAllowances.Res(addr, balanceAndAllowanceMap)
        } yield result).sendTo(sender)
      }

    case req @ SubmitOrder.Req(Some(raworder)) =>
      blocking {
        val marketHash = MarketPair(raworder.tokenS, raworder.tokenB).hashString
        (for {
          _ <- Future {
            if (accountCutoffState.isOrderCutoffByOwner(raworder.validSince)) {
              throw ErrorException(
                ERR_ORDER_VALIDATION_INVALID_CUTOFF,
                s"this address has set cutoff>=${raworder.getParams.validUntil}."
              )
            } else if (accountCutoffState.isOrderCutoffByTradingPair(
                         marketHash,
                         raworder.validSince
                       )) {
              throw ErrorException(
                ERR_ORDER_VALIDATION_INVALID_CUTOFF,
                s"the market ${raworder.tokenS}-${raworder.tokenB} " +
                  s"of this address has set cutoff > =${raworder.getParams.validUntil}."
              )
            }
          }
          canceled <- isOrderCanceled(raworder) //取消订单，单独查询以太坊
          _ = if (canceled)
            throw ErrorException(
              ERR_ORDER_VALIDATION_INVALID_CANCELED,
              s"this order has been canceled."
            )

          newRaworder = if (raworder.validSince > timeProvider
                              .getTimeSeconds()) {
            raworder.withStatus(STATUS_PENDING_ACTIVE)
          } else raworder

          resRawOrder <- (orderPersistenceActor ? req
            .copy(rawOrder = Some(newRaworder)))
            .mapAs[RawOrder]

          resOrder <- (resRawOrder.getState.status match {
            case STATUS_PENDING_ACTIVE =>
              Future.successful(resRawOrder.toOrder)
            case _ => resubmitOrder(resRawOrder)
          }).mapAs[Order]

          result = SubmitOrder.Res(Some(resOrder))
        } yield result) sendTo sender
      }

    case req @ CancelOrder.Req("", addr, _, None, _) =>
      blocking { //按照Owner取消订单
        (for {
          updatedOrders <- manager.cancelAllOrders()
          result = {
            if (updatedOrders.nonEmpty) CancelOrder.Res(ERR_NONE)
            else CancelOrder.Res(ERR_ORDER_NOT_EXIST)
          }
        } yield result).sendTo(sender)
      }

    case req @ CancelOrder
          .Req("", owner, _, Some(marketPair), _) =>
      blocking { //按照Owner-MarketPair取消订单
        (for {
          updatedOrders <- manager.cancelOrders(marketPair)
          result = {
            if (updatedOrders.nonEmpty) CancelOrder.Res(ERR_NONE)
            else CancelOrder.Res(ERR_ORDER_NOT_EXIST)
          }
        } yield result).sendTo(sender)
      }

    case req @ CancelOrder.Req(id, addr, status, _, _) =>
      blocking {
        assert(addr == owner)
        val originalSender = sender
        (for {
          // Make sure PENDING-ACTIVE orders can be cancelled.
          result <- (orderPersistenceActor ? req).mapAs[CancelOrder.Res]
          (successful, updatedOrders) <- manager.cancelOrder(req.id)
          _ = {
            if (successful) {
              marketManagerActor.tell(req, originalSender)
            } else {
              throw ErrorException(
                ERR_FAILED_HANDLE_MSG,
                s"no order found with id: ${req.id}"
              )
            }
          }
        } yield result) sendTo sender
      }

    // 为了减少以太坊的查询量，需要每个block汇总后再批量查询，因此不使用TransferEvent
    case req: AddressBalanceUpdated =>
      blocking {
        assert(req.address == owner)
        manager
          .setBalance(req.token, BigInt(req.balance.toByteArray))
      }

    case req: AddressAllowanceUpdated =>
      blocking {
        assert(req.address == owner)
        manager.setAllowance(req.token, BigInt(req.allowance.toByteArray))
      }

    // ownerCutoff
    case req @ CutoffEvent(Some(header), broker, owner, "", cutoff) //
        if broker == owner && header.txStatus == TX_STATUS_SUCCESS =>
      blocking {
        accountCutoffState.setCutoff(cutoff)
        manager.handleCutoff(cutoff)
      }

    // ownerTokenPairCutoff  tokenPair ！= ""
    case req @ CutoffEvent(Some(header), broker, owner, marketHash, cutoff) //
        if broker == owner && header.txStatus == TX_STATUS_SUCCESS =>
      blocking {
        accountCutoffState.setTradingPairCutoff(marketHash, req.cutoff)
        manager.handleCutoff(cutoff, marketHash)
      }

    // Currently we do not support broker-level cutoff
    case req @ CutoffEvent(Some(header), broker, owner, _, cutoff) //
        if broker != owner && header.txStatus == TX_STATUS_SUCCESS =>
      log.warning(s"not support this event yet: $req")

    case req: OrdersCancelledEvent
        if req.header.nonEmpty && req.getHeader.txStatus.isTxStatusSuccess =>
      for {
        orders <- dbModule.orderService.getOrders(req.orderHashes)
        _ = orders.foreach { o =>
          val req = CancelOrder.Req(
            id = o.hash,
            owner = o.owner,
            marketPair =
              Some(MarketPair(baseToken = o.tokenS, quoteToken = o.tokenB)),
            status = STATUS_ONCHAIN_CANCELLED_BY_USER
          )
          self ! req
        }
      } yield Unit

    case req: OrderFilledEvent //
        if req.header.nonEmpty && req.getHeader.txStatus == TX_STATUS_SUCCESS =>
      blocking {
        for {
          orderOpt <- dbModule.orderService.getOrder(req.orderHash)
          _ <- swap(orderOpt.map(resubmitOrder))
        } yield Unit
      }

    case req: MetadataChanged =>
      //将terminate的market的订单从内存中删除，terminate的市场等已经被停止删除了，
      // 因此不需要再发送给已经停止的market了，也不需要更改数据库状态，保持原状态就可以了
      val marketPairs =
        metadataManager
          .getMarkets(Set(MarketMetadata.Status.TERMINATED))
          .map(_.getMarketPair)
      marketPairs map { marketPair =>
        manager.purgeOrders(marketPair)
      }

  }

  private def resubmitOrder(rawOrder: RawOrder): Future[Order] = {
    val order = rawOrder.toOrder
    val orderId = order.id
    val matchable: Matchable = order
    log.debug(s"### submitOrder ${order}")
    for {
      getFilledAmountRes <- (ethereumQueryActor ? GetFilledAmount.Req(
        Seq(orderId)
      )).mapAs[GetFilledAmount.Res]

      filledAmountS = getFilledAmountRes.filledAmountSMap
        .getOrElse(orderId, ByteString.copyFrom("0".getBytes))

      adjusted = matchable.withFilledAmountS(filledAmountS)
      (successful, updatedOrders) <- manager.resubmitOrder(adjusted)
      updatedOrder = updatedOrders.getOrElse(orderId, adjusted)
      status = updatedOrder.status

      _ = log.debug(
        s"submit order result:  ${updatedOrder}",
        s"with ${updatedOrders.size} updated orders"
      )

      _ = if (!successful) {
        val error = status match {
          case STATUS_SOFT_CANCELLED_LOW_BALANCE => ERR_LOW_BALANCE
          case _ =>
            log.error(s"unexpected failure order status $status")
            ERR_INTERNAL_UNKNOWN
        }
        throw ErrorException(error, s"failed to submit order ${status}.")
      }

      result: Order = updatedOrder.copy(_reserved = None, _outstanding = None)
    } yield result
  }

  def isOrderCanceled(rawOrder: RawOrder) =
    for {
      res <- (ethereumQueryActor ? GetOrderCancellation.Req(
        broker = rawOrder.owner,
        orderHash = rawOrder.hash
      )).mapAs[GetOrderCancellation.Res]
    } yield res.cancelled

  private def swap[T](o: Option[Future[T]]): Future[Option[T]] =
    o.map(_.map(Some(_))).getOrElse(Future.successful(None))
}
