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
//TODO:如果刷新时间太长，或者读取次数超过一个值，就重新从以太坊读取balance/allowance，并reset这个时间和读取次数。

class AccountManagerActor2(
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
    val provider: BalanceAndAllowanceProvider)
    extends Actor
    with AccountManagerUpdatedOrdersProcessor
    with Stash
    with ActorLogging {

  import ErrorCode._

  implicit val processor: UpdatedOrdersProcessor = this

  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 second) {
      case e: Exception =>
        log.error(e.getMessage)
        Escalate
    }

  implicit val orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
  val manager = AccountManager2.default(owner)
  val accountCutoffState = new AccountCutoffStateImpl()

  def ethereumQueryActor = actors.get(EthereumQueryActor.name)
  def marketManagerActor = actors.get(MarketManagerActor.name)
  def orderPersistenceActor = actors.get(OrderPersistenceActor.name)

  override def preStart() = {
    self ! Notify("initialize")
  }

  def receive: Receive = initialReceive

  def initialReceive: Receive = {
    case Notify("initialize", _) =>
      val batchCutoffReq =
        BatchGetCutoffs.Req((metadataManager.getValidMarketPairs map {
          case (marketHash, marketPair) =>
            GetCutoff
              .Req(owner = owner, marketHash = marketHash)
        }).toSeq :+ GetCutoff.Req(owner = owner))

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
      context.become(normalReceive)
      unstashAll()
    case _ =>
      stash()
  }

  def normalReceive: Receive = LoggingReceive {
    case req @ Notify(KeepAliveActor.NOTIFY_MSG, _) =>
      sender ! req

    // case ActorRecover.RecoverOrderReq(Some(xraworder)) =>
    //   submitOrder(xraworder).map { _ =>
    //     ActorRecover.OrderRecoverResult(xraworder.hash, true)
    //   }.sendTo(sender)

    case GetBalanceAndAllowances.Req(addr, tokens, _) =>
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

    // case req @ SubmitOrder.Req(Some(raworder)) =>
    //   (for {
    //     //check通过再保存到数据库，以及后续处理
    //     _ <- Future { accountCutoffState.checkOrderCutoff(raworder) }
    //     _ <- checkOrderCanceled(raworder) //取消订单，单独查询以太坊
    //     newRaworder = if (raworder.validSince > timeProvider.getTimeSeconds()) {
    //       raworder.withStatus(STATUS_PENDING_ACTIVE)
    //     } else raworder

    //     resRawOrder <- (orderPersistenceActor ? req
    //       .copy(rawOrder = Some(newRaworder)))
    //       .mapAs[RawOrder]
    //     resOrder <- (resRawOrder.getState.status match {
    //       case STATUS_PENDING_ACTIVE =>
    //         Future.successful(resRawOrder.toOrder)
    //       case _ => submitOrder(resRawOrder)
    //     }).mapAs[Order]
    // //   } yield SubmitOrder.Res(Some(resOrder))) sendTo sender

    case req @ CancelOrder.Req("", addr, _, None, _) => //按照Owner取消订单
      (for {
        updatedOrders <- manager.cancelAllOrders()
        result = {
          if (updatedOrders.nonEmpty) CancelOrder.Res(ERR_NONE)
          else CancelOrder.Res(ERR_ORDER_NOT_EXIST)
        }
      } yield result).sendTo(sender)

    case req @ CancelOrder
          .Req("", owner, _, Some(marketPair), _) => //按照Owner-MarketPair取消订单
      (for {
        updatedOrders <- manager.cancelOrders(marketPair)
        result = {
          if (updatedOrders.nonEmpty) CancelOrder.Res(ERR_NONE)
          else CancelOrder.Res(ERR_ORDER_NOT_EXIST)
        }
      } yield result).sendTo(sender)

    case req @ CancelOrder.Req(id, addr, status, _, _) =>
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

    // //为了减少以太坊的查询量，需要每个block汇总后再批量查询，因此不使用TransferEvent
    case req: AddressBalanceUpdated =>
      assert(req.address == owner)
      manager
        .setBalance(req.token, BigInt(req.balance.toByteArray))

    case req: AddressAllowanceUpdated =>
      assert(req.address == owner)
      manager
        .setAllowance(req.token, BigInt(req.allowance.toByteArray))

    // //ownerCutoff
    case req @ CutoffEvent(Some(header), broker, owner, "", cutoff) //
        if broker == owner && header.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      accountCutoffState.setCutoff(cutoff)
      manager.handleCutoff(cutoff)

    // //ownerTokenPairCutoff  tokenPair ！= ""
    case req @ CutoffEvent(Some(header), broker, owner, marketHash, cutoff) //
        if broker == owner && header.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      accountCutoffState.setTradingPairCutoff(marketHash, req.cutoff)
      manager.handleCutoff(cutoff, marketHash)

    // Currently we do not support broker-level cutoff
    case req @ CutoffEvent(Some(header), broker, owner, _, cutoff) //
        if broker != owner && header.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      log.debug(s"received BrokerCutoffEvent $req")

    case req: OrderFilledEvent //
        if req.header.nonEmpty && req.getHeader.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      for {
        orderOpt <- dbModule.orderService.getOrder(req.orderHash)
        _ <- swap(orderOpt.map(submitOrder))
      } yield Unit
  }

  private def submitOrder(rawOrder: RawOrder): Future[Order] = {
    null
    //   val order = rawOrder.toOrder
    //   val matchable: Matchable = order
    //   log.debug(s"### submitOrder ${order}")
    //   for {
    //     _ <- if (matchable.amountFee > 0 && matchable.tokenS != matchable.tokenFee)
    //       getReserveManagers(Seq(matchable.tokenS, matchable.tokenFee))
    //     else
    //       getReserveManagers(Seq(matchable.tokenS))

    //     getFilledAmountRes <- (ethereumQueryActor ? GetFilledAmount.Req(
    //       Seq(matchable.id))).mapAs[GetFilledAmount.Res]

    //     filledAmountS = getFilledAmountRes.filledAmountSMap
    //       .getOrElse(matchable.id, ByteString.copyFrom("0".getBytes))

    //     _matchable = matchable.withFilledAmountS(filledAmountS)

    //     (successful, updatedOrders) = manager.synchronized {
    //       val res = if (orderPool.contains(order.id)) {
    //         manager.adjustOrder(_matchable.id, _matchable.outstanding.amountS)
    //       } else {
    //         manager.submitOrder(_matchable)
    //       }
    //       (res, orderPool.takeUpdatedOrders)
    //     }

    //     _ = if (!successful) {
    //       val error = updatedOrders(matchable.id).status match {
    //         case STATUS_INVALID_DATA => ERR_INVALID_ORDER_DATA
    //         case STATUS_UNSUPPORTED_MARKET => ERR_INVALID_MARKET
    //         case STATUS_SOFT_CANCELLED_TOO_MANY_ORDERS => ERR_TOO_MANY_ORDERS
    //         case STATUS_SOFT_CANCELLED_DUPLICIATE => ERR_ORDER_ALREADY_EXIST
    //         case other =>
    //           log.error(s"unexpected failure order status $other")
    //           ERR_INTERNAL_UNKNOWN
    //       }

    //       throw ErrorException(
    //         error,
    //         s"failed to submit order with status:${matchable.status} in AccountManagerActor.")
    //     }

    //     _ = log.debug(
    //       s"updated matchable ${_matchable}\nfound ${updatedOrders.size} updated orders")

    //     res <- processOrders(updatedOrders)

    //     matchable_ = updatedOrders.getOrElse(matchable.id, _matchable)
    //     order_ : Order = matchable_.copy(_reserved = None, _outstanding = None)
    //   } yield order_
  }

  // // TODO(hongyu): this is a bug here - if a order reservers two tokens, A, and B, if A's allowance
  // // becomes very small, then we also need to release all reserved token B for this order.
  // private def updateBalanceOrAllowance(
  //   token: String)(retrieveUpdatedOrders: => Map[String, Matchable]) =
  //   for {
  //     _ <- getReserveManagers(Seq(token))
  //     updatedOrders = retrieveUpdatedOrders
  //     _ <- Future.sequence {
  //       updatedOrders.values.map { order =>
  //         order.status match {
  //           case STATUS_SOFT_CANCELLED_LOW_BALANCE |
  //             STATUS_SOFT_CANCELLED_LOW_FEE_BALANCE | //
  //             STATUS_PENDING | //
  //             STATUS_COMPLETELY_FILLED | //
  //             STATUS_PARTIALLY_FILLED =>
  //             Future.unit

  //           case status =>
  //             val msg =
  //               s"unexpected order status caused by balance/allowance upate: $status"
  //             log.error(msg)
  //             throw ErrorException(ERR_INTERNAL_UNKNOWN, msg)
  //         }
  //       }
  //     }
  //     _ <- processOrders(updatedOrders)
  //   } yield Unit

  // def checkOrderCanceled(rawOrder: RawOrder) =
  //   for {
  //     res <- (ethereumQueryActor ? GetOrderCancellation.Req(
  //       broker = address,
  //       orderHash = rawOrder.hash)).mapAs[GetOrderCancellation.Res]
  //   } yield if (res.cancelled)
  //     throw ErrorException(
  //       ERR_ORDER_VALIDATION_INVALID_CANCELED,
  //       s"this order has been canceled.")

  // TODO:terminate market则需要将订单从内存中删除,但是不从数据库删除

  private def swap[T](o: Option[Future[T]]): Future[Option[T]] =
    o.map(_.map(Some(_))).getOrElse(Future.successful(None))
}
