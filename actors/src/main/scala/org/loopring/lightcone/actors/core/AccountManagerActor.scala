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

import akka.actor.SupervisorStrategy._
import akka.actor._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.lib.MarketHashProvider._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto.OrderStatus._
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// Owner: Hongyu
class AccountManagerActor(
    address: String
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dustEvaluator: DustOrderEvaluator,
    val dbModule: DatabaseModule,
    val metadataManager: MetadataManager)
    extends Actor
    with Stash
    with ActorLogging {

  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 second) {
      case e: Exception =>
        log.error(e.getMessage)
        Escalate
    }

  implicit val orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
  val manager = AccountManager.default
  val accountCutoffState = new AccountCutoffStateImpl()

  protected def ethereumQueryActor = actors.get(EthereumQueryActor.name)
  protected def marketManagerActor = actors.get(MarketManagerActor.name)
  protected def orderPersistenceActor = actors.get(OrderPersistenceActor.name)

  override def preStart() = {
    val cutoffReqs = (metadataManager.getValidMarketIds map { m =>
      for {
        res <- (ethereumQueryActor ? GetCutoff.Req(
          broker = address,
          owner = address,
          marketKey = m._2.keyHex()
        )).mapAs[GetCutoff.Res]
      } yield {
        val cutoff: BigInt = res.cutoff
        accountCutoffState.setTradingPairCutoff(
          m._2.key(),
          cutoff.toLong
        )
      }
    }).toSeq :+
      (for {
        res <- (ethereumQueryActor ? GetCutoff.Req(
          broker = address,
          owner = address
        )).mapAs[GetCutoff.Res]
      } yield {
        val cutoff: BigInt = res.cutoff
        accountCutoffState.setCutoff(cutoff.toLong)
      })
    Future.sequence(cutoffReqs) onComplete {
      case Success(res) =>
        self ! Notify("initialized")
      case Failure(e) =>
        log.error(s"failed to start AccountManagerActor: ${e.getMessage}")
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"failed to start AccountManagerActor: ${e.getMessage}"
        )
    }
  }

  def receive: Receive = initialReceive

  def initialReceive: Receive = {
    case Notify("initialized", _) =>
      unstashAll()
      context.become(normalReceive)
    case _ =>
      stash()
  }

  def normalReceive: Receive = LoggingReceive {
    case req @ Notify(KeepAliveActor.NOTIFY_MSG, _) =>
      sender ! req

    case ActorRecover.RecoverOrderReq(Some(xraworder)) =>
      submitOrAdjustOrder(xraworder).map { _ =>
        ActorRecover.OrderRecoverResult(xraworder.id, true)
      }.sendTo(sender)

    case GetBalanceAndAllowances.Req(addr, tokens, _) =>
      assert(addr == address)
      (for {
        managers <- getTokenManagers(tokens)
        _ = assert(tokens.size == managers.size)
        balanceAndAllowanceMap = tokens.zip(managers).toMap.map {
          case (token, manager) =>
            token -> BalanceAndAllowance(
              manager.getBalance(),
              manager.getAllowance(),
              manager.getAvailableBalance(),
              manager.getAvailableAllowance()
            )
        }
      } yield {
        GetBalanceAndAllowances.Res(address, balanceAndAllowanceMap)
      }).sendTo(sender)

    case req @ SubmitOrder.Req(Some(raworder)) =>
      (for {
        _ <- for {
          //check通过再保存到数据库，以及后续处理
          _ <- Future { accountCutoffState.isOrderCutoff(raworder) }
          _ <- isOrderCanceled(raworder) //取消订单，单独查询以太坊
        } yield Unit
        newRaworder = if (raworder.validSince > timeProvider.getTimeSeconds()) {
          raworder.copy(
            state = Some(
              raworder.getState
                .copy(status = OrderStatus.STATUS_PENDING_ACTIVE)
            )
          )
        } else raworder

        res <- for {
          resRawOrder <- (orderPersistenceActor ? req
            .copy(rawOrder = Some(newRaworder)))
            .mapAs[RawOrder]
          resOrder <- (resRawOrder.getState.status match {
            case STATUS_PENDING_ACTIVE =>
              val order: Order = resRawOrder
              Future.successful(order)
            case _ => submitOrAdjustOrder(resRawOrder)
          }).mapAs[Order]
        } yield SubmitOrder.Res(Some(resOrder))

      } yield res) sendTo sender

    case req: CancelOrder.Req =>
      val originalSender = sender
      (for {
        _ <- Future.successful(assert(req.owner == address))
        persistenceRes <- (orderPersistenceActor ? req)
          .mapAs[CancelOrder.Res]
        (cancelRes, updatedOrders) = manager
          .handleChangeEventThenGetUpdatedOrders(req)
        _ <- processUpdatedOrders(updatedOrders - req.id)
        _ = if (cancelRes) {
          marketManagerActor.tell(req, originalSender)
        } else {
          //在目前没有使用eventlog的情况下，哪怕manager中并没有该订单，则仍需要发送到MarketManager
          marketManagerActor ! req
          throw ErrorException(
            ERR_FAILED_HANDLE_MSG,
            s"no order found with id: ${req.id}"
          )
        }
      } yield persistenceRes) sendTo sender

    //为了减少以太坊的查询量，需要每个block汇总后再批量查询，因此不使用TransferEvent
    case req @ AddressBalanceUpdated(addr, token, newBalance) =>
      (for {
        _ <- Future.successful(assert(addr == address))
      } yield updateBalanceOrAllowance(token, req)) sendTo sender

    case req @ AddressAllowanceUpdated(addr, token, newBalance) =>
      (for {
        _ <- Future.successful(assert(addr == address))
      } yield updateBalanceOrAllowance(token, req)) sendTo sender

    //ownerCutoff
    case req @ CutoffEvent(Some(header), broker, owner, "", cutoff)
        if broker == owner && header.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      log.debug(s"received OwnerCutoffEvent $req")
      accountCutoffState.setCutoff(cutoff)

    //ownerTokenPairCutoff  tokenPair ！= ""
    case req @ CutoffEvent(Some(header), broker, owner, tokenPair, cutoff)
        if broker == owner && header.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      log.debug(s"received OwnerTokenPairCutoffEvent $req")
      accountCutoffState
        .setTradingPairCutoff(Numeric.toBigInt(req.tradingPair), req.cutoff)

    //brokerCutoff
    //todo:暂时不处理
    case req @ CutoffEvent(Some(header), broker, owner, _, cutoff)
        if broker != owner && header.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      log.debug(s"received BrokerCutoffEvent $req")

    case req: OrderFilledEvent
        if req.header.nonEmpty && req.getHeader.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      log.debug(s"received OrderFilledEvent ${req}")
      //收到filledEvent后，submitOrAdjustOrder会调用adjust方法
      for {
        orderOpt <- dbModule.orderService.getOrder(req.orderHash)
      } yield orderOpt.map(o => submitOrAdjustOrder(o))
  }

  private def submitOrAdjustOrder(rawOrder: RawOrder): Future[Order] = {
    val order: Order = rawOrder
    val matchable: Matchable = order
    log.debug(s"### submitOrAdjustOrder ${order}")
    for {
      _ <- getTokenManagers(Seq(matchable.tokenS))
      _ <- if (matchable.amountFee > 0 && matchable.tokenS != matchable.tokenFee)
        getTokenManagers(Seq(matchable.tokenFee))
      else
        Future.successful(Unit)

      // Update the order's _outstanding field.
      getFilledAmountRes <- (ethereumQueryActor ? GetFilledAmount.Req(
        Seq(matchable.id)
      )).mapAs[GetFilledAmount.Res]

      filledAmountS = getFilledAmountRes.filledAmountSMap(matchable.id)
      _ = log.debug(
        s"ethereumQueryActor GetFilledAmount.Res $getFilledAmountRes"
      )

      _matchable = matchable.withFilledAmountS(filledAmountS)

      state = rawOrder.state
        .getOrElse(
          RawOrder.State(
            createdAt = timeProvider.getTimeMillis(),
            updatedAt = timeProvider.getTimeMillis(),
            status = OrderStatus.STATUS_NEW
          )
        )
        .copy(
          outstandingAmountS = filledAmountS,
          outstandingAmountB = _matchable.outstanding.amountB,
          outstandingAmountFee = _matchable.outstanding.amountFee
        )

      _ <- dbModule.orderService.updateAmount(rawOrder.id, state = state)

      _ = log.debug(s"submitting order to AccountManager: ${_matchable}")
      (successful, updatedOrders) = manager
        .handleChangeEventThenGetUpdatedOrders(_matchable)
      _ = if (!successful)
        throw ErrorException(Error(matchable.status))
      _ = assert(updatedOrders.contains(_matchable.id))
      _ = log.debug(
        s"updatedOrders: ${updatedOrders.size} assert contains order:  ${updatedOrders(_matchable.id)}"
      )
      res <- processUpdatedOrders(updatedOrders)
      matchable_ = updatedOrders(_matchable.id)
      order_ : Order = matchable_.copy(_reserved = None, _outstanding = None)
    } yield order_
  }

  private def processUpdatedOrders(updatedOrders: Map[String, Matchable]) = {
    Future.sequence {
      updatedOrders.map { o =>
        for {
          //需要更新到数据库
          _ <- dbModule.orderService.updateOrderStatus(o._2.id, o._2.status)
        } yield {
          val order = o._2
          order.status match {
            //新订单、或者匹配一部分的订单
            case STATUS_NEW | STATUS_PENDING | STATUS_PARTIALLY_FILLED =>
              marketManagerActor ! SubmitSimpleOrder(
                order = Some(o._2.copy(_reserved = None, _outstanding = None))
              )
            //取消的订单
            case STATUS_EXPIRED | STATUS_DUST_ORDER |
                STATUS_SOFT_CANCELLED_BY_USER |
                STATUS_SOFT_CANCELLED_BY_USER_TRADING_PAIR |
                STATUS_ONCHAIN_CANCELLED_BY_USER |
                STATUS_ONCHAIN_CANCELLED_BY_USER_TRADING_PAIR |
                STATUS_TOO_MANY_RING_FAILURES | STATUS_CANCELLED_LOW_BALANCE |
                STATUS_CANCELLED_LOW_FEE_BALANCE |
                STATUS_CANCELLED_TOO_MANY_ORDERS |
                STATUS_CANCELLED_TOO_MANY_FAILED_SETTLEMENTS |
                STATUS_CANCELLED_DUPLICIATE =>
              marketManagerActor ! CancelOrder.Req(
                id = order.id,
                marketId = Some(MarketId(order.tokenS, order.tokenB))
              )
            //完全匹配，则仍然是删掉该订单
            case STATUS_COMPLETELY_FILLED =>
              marketManagerActor ! CancelOrder.Req(
                id = order.id,
                marketId = Some(MarketId(order.tokenS, order.tokenB))
              )
            case _ =>
              throw ErrorException(
                ErrorCode.ERR_INVALID_ORDER_DATA,
                s"not supproted order.status in AccountManager"
              )
          }
        }
      }
    }
  }

  private def getTokenManagers(
      tokens: Seq[String]
    ): Future[Seq[AccountTokenManager]] = {
    val tokensWithoutMaster =
      tokens.filterNot(token => manager.hasTokenManager(token))
    for {
      res <- if (tokensWithoutMaster.nonEmpty) {
        (ethereumQueryActor ? GetBalanceAndAllowances.Req(
          address,
          tokensWithoutMaster
        )).mapAs[GetBalanceAndAllowances.Res]
      } else {
        Future.successful(GetBalanceAndAllowances.Res())
      }
      tms = tokensWithoutMaster.map(
        token =>
          new AccountTokenManagerImpl(
            token,
            config.getInt("account_manager.max_order_num")
          )
      )
      _ = tms.foreach(tm => {
        val ba = res.balanceAndAllowanceMap(tm.token)
        tm.setBalanceAndAllowance(ba.balance, ba.allowance)
        manager.getOrUpdateTokenManager(tm.token, tm)
      })
      tokenMangers = tokens.map(manager.getTokenManager)
    } yield tokenMangers
  }

  private def updateBalanceOrAllowance[T](
      token: String,
      req: T
    ) =
    for {
      tm <- getTokenManagers(Seq(token))
      (_, updatedOrders) = manager.handleChangeEventThenGetUpdatedOrders(req)
      _ <- Future.sequence {
        updatedOrders.values.map { order =>
          order.status match {
            case STATUS_CANCELLED_LOW_BALANCE |
                STATUS_CANCELLED_LOW_FEE_BALANCE | STATUS_PENDING |
                STATUS_COMPLETELY_FILLED | STATUS_PARTIALLY_FILLED =>
              Future.successful(Unit)
            case status =>
              log.error(
                s"unexpected order status caused by balance/allowance upate: $status"
              )
              throw ErrorException(
                ErrorCode.ERR_INTERNAL_UNKNOWN,
                s"unexpected order status caused by balance/allowance upate: $status"
              )
          }
        }
      }
      _ <- processUpdatedOrders(updatedOrders)
    } yield Unit

  def isOrderCanceled(rawOrder: RawOrder) =
    for {
      res <- (ethereumQueryActor ? GetOrderCancellation.Req(
        broker = address,
        orderHash = rawOrder.hash
      )).mapAs[GetOrderCancellation.Res]
    } yield
      if (res.cancelled)
        throw ErrorException(
          ERR_ORDER_VALIDATION_INVALID_CUTOFF,
          s"this order has been canceled."
        )

}
