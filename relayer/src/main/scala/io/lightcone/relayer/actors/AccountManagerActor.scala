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
import com.google.protobuf.ByteString
import io.lightcone.relayer.base._
import io.lightcone.relayer.base.safefuture._
import io.lightcone.relayer.data._
import io.lightcone.core._
import io.lightcone.core.MarketManager.MatchResult
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule

import io.lightcone.proto._
import org.web3j.utils.Numeric

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// Owner: Hongyu
//TODO:如果刷新时间太长，或者读取次数超过一个值，就重新从以太坊读取balance/allowance，并reset这个时间和读取次数。
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

  import ErrorCode._
  import OrderStatus._

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
    self ! Notify("initialize")
  }

  def receive: Receive = initialReceive

  def initialReceive: Receive = {
    case Notify("initialize", _) =>
      val batchCutoffReq =
        BatchGetCutoffs.Req((metadataManager.getValidMarketPairs map {
          case (marketHash, marketPair) =>
            GetCutoff
              .Req(broker = address, owner = address, marketHash = marketHash)
        }).toSeq :+ GetCutoff.Req(broker = address, owner = address))

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

    case ActorRecover.RecoverOrderReq(Some(xraworder)) =>
      submitOrder(xraworder).map { _ =>
        ActorRecover.OrderRecoverResult(xraworder.hash, true)
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
        //check通过再保存到数据库，以及后续处理
        _ <- Future { accountCutoffState.checkOrderCutoff(raworder) }
        _ <- checkOrderCanceled(raworder) //取消订单，单独查询以太坊
        newRaworder = if (raworder.validSince > timeProvider.getTimeSeconds()) {
          raworder.withStatus(STATUS_PENDING_ACTIVE)
        } else raworder

        resRawOrder <- (orderPersistenceActor ? req
          .copy(rawOrder = Some(newRaworder)))
          .mapAs[RawOrder]
        resOrder <- (resRawOrder.getState.status match {
          case STATUS_PENDING_ACTIVE =>
            Future.successful(resRawOrder.toOrder)
          case _ => submitOrder(resRawOrder)
        }).mapAs[Order]
      } yield SubmitOrder.Res(Some(resOrder))) sendTo sender

    case req @ CancelOrder.Req("", owner, _, None, _) => //按照Owner取消订单
      val f = for {
        _ <- Future { assert(req.owner == address) }
        (res, updatedOrders) = manager.synchronized {
          (manager.cancelAllOrders(), orderPool.takeUpdatedOrdersAsMap)
        }
        _ <- processUpdatedOrders(updatedOrders)
      } yield CancelOrder.Res(ERR_NONE)
      f.sendTo(sender)

    case req @ CancelOrder
          .Req("", owner, _, Some(marketPair), _) => //按照Owner-MarketPair取消订单
      val f = for {
        _ <- Future { assert(req.owner == address) }
        (res, updatedOrders) = manager.synchronized {
          (
            manager.cancelOrdersInMarket(MarketHash(marketPair).toString),
            orderPool.takeUpdatedOrdersAsMap
          )
        }
        _ <- processUpdatedOrders(updatedOrders)
      } yield CancelOrder.Res(ERR_NONE)
      f.sendTo(sender)

    case req @ CancelOrder.Req(id, owner, status, _, _) =>
      val originalSender = sender
      (for {
        _ <- Future.successful(assert(req.owner == address))
        // Make sure PENDING-ACTIVE orders can be cancelled.
        persistenceRes <- (orderPersistenceActor ? req)
          .mapAs[CancelOrder.Res]

        (res, updatedOrders) = manager.synchronized {
          (manager.cancelOrder(req.id), orderPool.takeUpdatedOrdersAsMap())
        }

        _ <- processUpdatedOrders(updatedOrders - req.id)
        _ = if (res) {
          marketManagerActor.tell(req, originalSender)
        } else {
          throw ErrorException(
            ERR_FAILED_HANDLE_MSG,
            s"no order found with id: ${req.id}"
          )
        }
      } yield persistenceRes) sendTo sender

    //为了减少以太坊的查询量，需要每个block汇总后再批量查询，因此不使用TransferEvent
    case req: AddressBalanceUpdated =>
      assert(req.address == address)

      updateBalanceOrAllowance(req.token) {
        val tm = manager.getTokenManager(req.token)
        manager.synchronized {
          tm.setBalance(BigInt(req.balance.toByteArray))
          orderPool.takeUpdatedOrdersAsMap
        }
      }

    case req: AddressAllowanceUpdated =>
      assert(req.address == address)

      updateBalanceOrAllowance(req.token) {
        val tm = manager.getTokenManager(req.token)
        manager.synchronized {
          tm.setAllowance(BigInt(req.allowance.toByteArray))
          orderPool.takeUpdatedOrdersAsMap
        }
      }

    //ownerCutoff
    case req @ CutoffEvent(Some(header), broker, owner, "", cutoff)
        if broker == owner && header.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      log.debug(s"received OwnerCutoffEvent $req")
      accountCutoffState.setCutoff(cutoff)

      val updatedOrders = manager.synchronized {
        manager.handleCutoff(cutoff)
        orderPool.takeUpdatedOrdersAsMap
      }
      processUpdatedOrders(updatedOrders)

    //ownerTokenPairCutoff  tokenPair ！= ""
    case req @ CutoffEvent(Some(header), broker, owner, marketHash, cutoff)
        if broker == owner && header.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      log.debug(s"received OwnerTokenPairCutoffEvent $req")
      accountCutoffState
        .setTradingPairCutoff(marketHash, req.cutoff)

      val updatedOrders = manager.synchronized {
        manager.handleCutoff(cutoff, marketHash)
        orderPool.takeUpdatedOrdersAsMap
      }
      processUpdatedOrders(updatedOrders)

    //Currently we do not support broker-level cutoff
    case req @ CutoffEvent(Some(header), broker, owner, _, cutoff)
        if broker != owner && header.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      log.debug(s"received BrokerCutoffEvent $req")

    case req: OrderFilledEvent
        if req.header.nonEmpty && req.getHeader.txStatus == TxStatus.TX_STATUS_SUCCESS =>
      log.debug(s"received OrderFilledEvent ${req}")
      for {
        orderOpt <- dbModule.orderService.getOrder(req.orderHash)
      } yield orderOpt.map(o => submitOrder(o))
  }

  private def submitOrder(rawOrder: RawOrder): Future[Order] = {
    val order = rawOrder.toOrder
    val matchable: Matchable = order
    log.debug(s"### submitOrder ${order}")
    for {
      _ <- if (matchable.amountFee > 0 && matchable.tokenS != matchable.tokenFee)
        getTokenManagers(Seq(matchable.tokenS, matchable.tokenFee))
      else
        getTokenManagers(Seq(matchable.tokenS))

      getFilledAmountRes <- (ethereumQueryActor ? GetFilledAmount.Req(
        Seq(matchable.id)
      )).mapAs[GetFilledAmount.Res]

      filledAmountS = getFilledAmountRes.filledAmountSMap
        .getOrElse(matchable.id, ByteString.copyFrom("0".getBytes))

      _matchable = matchable.withFilledAmountS(filledAmountS)

      (successful, updatedOrders) = manager.synchronized {
        val res = if (orderPool.contains(order.id)) {
          manager.adjustOrder(_matchable.id, _matchable.outstanding.amountS)
        } else {
          manager.submitOrder(_matchable)
        }
        (res, orderPool.takeUpdatedOrdersAsMap)
      }

      _ = if (!successful) {
        val error = updatedOrders(matchable.id).status match {
          case STATUS_INVALID_DATA                   => ERR_INVALID_ORDER_DATA
          case STATUS_UNSUPPORTED_MARKET             => ERR_INVALID_MARKET
          case STATUS_SOFT_CANCELLED_TOO_MANY_ORDERS => ERR_TOO_MANY_ORDERS
          case STATUS_SOFT_CANCELLED_DUPLICIATE      => ERR_ORDER_ALREADY_EXIST
          case other =>
            log.error(s"unexpected failure order status $other")
            ERR_INTERNAL_UNKNOWN
        }

        throw ErrorException(
          error,
          s"failed to submit order with status:${matchable.status} in AccountManagerActor."
        )
      }

      _ = log.debug(
        s"updated matchable ${_matchable}\nfound ${updatedOrders.size} updated orders"
      )

      res <- processUpdatedOrders(updatedOrders)

      matchable_ = updatedOrders.getOrElse(matchable.id, _matchable)
      order_ : Order = matchable_.copy(_reserved = None, _outstanding = None)
    } yield order_
  }

  private def processUpdatedOrders(updatedOrders: Map[String, Matchable]) =
    Future.sequence {
      updatedOrders.map {
        case (id, order) =>
          val state = RawOrder.State(
            actualAmountS = order.actual.amountS,
            actualAmountB = order.actual.amountB,
            actualAmountFee = order.actual.amountFee,
            outstandingAmountS = order.outstanding.amountS,
            outstandingAmountB = order.outstanding.amountB,
            outstandingAmountFee = order.outstanding.amountFee,
            status = order.status
          )

          for {
            //需要更新到数据库
            //TODO(yongfeng): 暂时添加接口，需要永丰根据目前的使用优化dal的接口
            _ <- dbModule.orderService.updateOrderState(order.id, state)
            _ <- order.status match {
              case STATUS_NEW | //
                  STATUS_PENDING | //
                  STATUS_PARTIALLY_FILLED =>
                log.debug(s"submitting order id=${order.id} to MMA")
                val order_ = order.copy(_reserved = None, _outstanding = None)
                for {
                  matchRes <- (marketManagerActor ? SubmitSimpleOrder(
                    order = Some(order_)
                  )).mapAs[MatchResult]
                  _ = matchRes.taker.status match {
                    case STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES =>
                      self ! CancelOrder.Req(
                        matchRes.taker.id,
                        address,
                        matchRes.taker.status,
                        Some(MarketPair(order.tokenS, order.tokenB))
                      )
                    case _ =>
                  }
                } yield Unit

              case STATUS_EXPIRED | //
                  STATUS_DUST_ORDER | //
                  STATUS_COMPLETELY_FILLED | //
                  STATUS_SOFT_CANCELLED_BY_USER | //
                  STATUS_SOFT_CANCELLED_BY_USER_TRADING_PAIR | //
                  STATUS_ONCHAIN_CANCELLED_BY_USER | //
                  STATUS_ONCHAIN_CANCELLED_BY_USER_TRADING_PAIR | //
                  STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES | //
                  STATUS_SOFT_CANCELLED_LOW_BALANCE | //
                  STATUS_SOFT_CANCELLED_LOW_FEE_BALANCE | //
                  STATUS_SOFT_CANCELLED_TOO_MANY_ORDERS | //
                  STATUS_SOFT_CANCELLED_DUPLICIATE =>
                log.debug(
                  s"cancelling order id=${order.id} status=${order.status}"
                )
                val marketPair = MarketPair(order.tokenS, order.tokenB)
                marketManagerActor ? CancelOrder.Req(
                  id = order.id,
                  marketPair = Some(marketPair)
                )

              case status =>
                throw ErrorException(
                  ERR_INVALID_ORDER_DATA,
                  s"unexpected order status: $status in: $order"
                )
            }
          } yield Unit
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
      _ = tms.foreach { tm =>
        val ba = res.balanceAndAllowanceMap(tm.token)
        tm.setBalanceAndAllowance(ba.balance, ba.allowance)
        manager.getOrUpdateTokenManager(tm)
      }
      tokenMangers = tokens.map(manager.getTokenManager)
    } yield tokenMangers
  }

  private def updateBalanceOrAllowance(
      token: String
    )(retrieveUpdatedOrders: => Map[String, Matchable]
    ) =
    for {
      _ <- getTokenManagers(Seq(token))
      updatedOrders = retrieveUpdatedOrders
      _ <- Future.sequence {
        updatedOrders.values.map { order =>
          order.status match {
            case STATUS_SOFT_CANCELLED_LOW_BALANCE |
                STATUS_SOFT_CANCELLED_LOW_FEE_BALANCE | //
                STATUS_PENDING | //
                STATUS_COMPLETELY_FILLED | //
                STATUS_PARTIALLY_FILLED =>
              Future.unit

            case status =>
              val msg =
                s"unexpected order status caused by balance/allowance upate: $status"
              log.error(msg)
              throw ErrorException(ERR_INTERNAL_UNKNOWN, msg)
          }
        }
      }
      _ <- processUpdatedOrders(updatedOrders)
    } yield Unit

  def checkOrderCanceled(rawOrder: RawOrder) =
    for {
      res <- (ethereumQueryActor ? GetOrderCancellation.Req(
        broker = address,
        orderHash = rawOrder.hash
      )).mapAs[GetOrderCancellation.Res]
    } yield
      if (res.cancelled)
        throw ErrorException(
          ERR_ORDER_VALIDATION_INVALID_CANCELED,
          s"this order has been canceled."
        )

  // TODO:terminate market则需要将订单从内存中删除,但是不从数据库删除

}
