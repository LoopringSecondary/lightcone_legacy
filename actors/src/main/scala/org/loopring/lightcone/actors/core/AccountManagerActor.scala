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

import akka.actor.SupervisorStrategy.Escalate
import akka.actor._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.proto.XOrderStatus._
import org.loopring.lightcone.proto._

import scala.concurrent._
import scala.concurrent.duration._

// main owner: 于红雨
class AccountManagerActor(
    address: String
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dustEvaluator: DustOrderEvaluator)
    extends Actor
    with ActorLogging {

  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 second) {
      case e: Exception ⇒
        log.error(e.getMessage)
        Escalate
    }

  implicit val orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
  val manager = AccountManager.default

  protected def ethereumQueryActor = actors.get(EthereumQueryActor.name)
  protected def marketManagerActor = actors.get(MarketManagerActor.name)

  def receive: Receive = LoggingReceive {

    case XRecover.RecoverOrderReq(Some(xraworder)) =>
      submitOrder(xraworder).map { _ =>
        XRecover.RecoverOrderRes(xraworder.id, true)
      }.sendTo(sender)

    case XGetBalanceAndAllowancesReq(addr, tokens) =>
      assert(addr == address)
      (for {
        managers <- Future.sequence(tokens.map(getTokenManager))
        _ = assert(tokens.size == managers.size)
        balanceAndAllowanceMap = tokens.zip(managers).toMap.map {
          case (token, manager) =>
            token -> XBalanceAndAllowance(
              manager.getBalance(),
              manager.getAllowance(),
              manager.getAvailableBalance(),
              manager.getAvailableAllowance()
            )
        }
      } yield {
        XGetBalanceAndAllowancesRes(address, balanceAndAllowanceMap)
      }).sendTo(sender)

    case XSubmitSimpleOrderReq(_, Some(xorder)) =>
      submitOrder(xorder).sendTo(sender)

    case req: XCancelOrderReq =>
      assert(req.owner == address)
      if (manager.cancelOrder(req.id)) {
        marketManagerActor forward req
      } else {
        Future.failed(
          ErrorException(
            ERR_FAILED_HANDLE_MSG,
            s"no order found with id: ${req.id}"
          )
        ) sendTo sender
      }

    case XAddressBalanceUpdated(addr, token, newBalance) =>
      assert(addr == address)
      updateBalanceOrAllowance(token, newBalance, _.setBalance(_))

    case XAddressAllowanceUpdated(addr, token, newBalance) =>
      assert(addr == address)
      updateBalanceOrAllowance(token, newBalance, _.setAllowance(_))
  }

  private def submitOrder(xorder: XOrder): Future[XSubmitOrderRes] = {
    val order: Order = xorder
    for {
      _ <- getTokenManager(order.tokenS)
      _ <- if (order.amountFee > 0 && order.tokenS != order.tokenFee)
        getTokenManager(order.tokenFee)
      else
        Future.successful(Unit)

      // Update the order's _outstanding field.
      orderHistoryRes <- (ethereumQueryActor ? XGetOrderFilledAmountReq(
        order.id
      )).mapAs[XGetOrderFilledAmountRes]

      _ = log.info(s"order history: orderHistoryRes")

      _order = order.withFilledAmountS(orderHistoryRes.filledAmountS)

      _ = log.info(s"submitting order to AccountManager: ${_order}")
      successful = manager.submitOrder(_order)
      _ = log.info(s"successful: $successful")
      _ = log.info(
        "orderPool updatdOrders: " + orderPool.getUpdatedOrders.mkString(", ")
      )
      updatedOrders = orderPool.takeUpdatedOrdersAsMap()
      _ = log.info(
        "orderPool updatedOrders: " + updatedOrders.mkString(", ")
      )
      _ = assert(updatedOrders.contains(_order.id))
      _ = log.info(
        "assert contains order: "
      )
      order_ = updatedOrders(_order.id)
      xorder_ : XOrder = order_.copy(_reserved = None, _outstanding = None)
      _ = log.info(
        s"assert contains order:  ${order_}, ${xorder_}"
      )
    } yield {
      if (successful) {
        log.info(s"submitting order to market manager actor: $order_")
        marketManagerActor ! XSubmitSimpleOrderReq("", Some(xorder_))
        XSubmitOrderRes(order = Some(xorder_))
      } else {
        throw ErrorException(
          XError(convertOrderStatusToErrorCode(order.status))
        )
      }
    }
  }

  private def convertOrderStatusToErrorCode(status: XOrderStatus): XErrorCode =
    status match {
      case STATUS_INVALID_DATA              => ERR_INVALID_ORDER_DATA
      case STATUS_UNSUPPORTED_MARKET        => ERR_INVALID_MARKET
      case STATUS_CANCELLED_TOO_MANY_ORDERS => ERR_TOO_MANY_ORDERS
      case STATUS_CANCELLED_DUPLICIATE      => ERR_ORDER_ALREADY_EXIST
      case _                                => ERR_INTERNAL_UNKNOWN
    }

  //todo:需要处理同时请求的问题
  private def getTokenManager(token: String): Future[AccountTokenManager] = {
    if (manager.hasTokenManager(token)) {
      Future.successful(manager.getTokenManager(token))
    } else {
      log.debug(s"getTokenManager0 ${token}")
      for {
        res <- (ethereumQueryActor ? XGetBalanceAndAllowancesReq(
          address,
          Seq(token)
        )).mapAs[XGetBalanceAndAllowancesRes]
        tm = new AccountTokenManagerImpl(token, 1000)
        ba: BalanceAndAllowance = res.balanceAndAllowanceMap(token)
        _ = tm.setBalanceAndAllowance(ba.balance, ba.allowance)
        tokenManager = manager.addTokenManager(tm)
        _ = log.debug(s"getTokenManager5 ${token}")

      } yield tokenManager
    }
  }

  private def updateBalanceOrAllowance(
      token: String,
      amount: BigInt,
      method: (AccountTokenManager, BigInt) => Unit
    ) =
    for {
      tm <- getTokenManager(token)
      _ = method(tm, amount)
      updatedOrders = orderPool.takeUpdatedOrders()
    } yield {
      updatedOrders.foreach { order =>
        order.status match {
          case STATUS_CANCELLED_LOW_BALANCE |
              STATUS_CANCELLED_LOW_FEE_BALANCE =>
            marketManagerActor ! XCancelOrderReq(
              id = order.id,
              marketId = Some(XMarketId(order.tokenS, order.tokenB))
            )

          case STATUS_PENDING =>
            //allowance的改变需要更新到marketManager
            marketManagerActor ! XSubmitSimpleOrderReq(order = Some(order))

          case status =>
            log.error(
              s"unexpected order status caused by balance/allowance upate: $status"
            )
        }
      }
    }
}
