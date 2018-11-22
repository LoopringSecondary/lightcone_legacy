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
import akka.event.LoggingReceive
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core.XOrderStatus._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.deployment._

import scala.concurrent._

object AccountManagerActor {
  val name = "account_manager"
}

class AccountManagerActor(
    val address: String,
    val recoverBatchSize: Int,
    val skipRecovery: Boolean = false
)(
    implicit
    val ec: ExecutionContext,
    val timeout: Timeout,
    val dustEvaluator: DustOrderEvaluator
)
  extends Actor
  with ActorLogging
  with OrderRecoverySupport {
  val ownerOfOrders = Some(address)

  implicit val orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
  val manager = AccountManager.default

  protected var orderDbManagerActor: ActorSelection = _
  protected var accountBalanceActor: ActorSelection = _
  protected var orderHistoryActor: ActorSelection = _
  protected var marketManagerActor: ActorSelection = _

  def receive: Receive = LoggingReceive {

    case XActorDependencyReady(paths) ⇒
      log.info(s"actor dependency ready: $paths")
      assert(paths.size == 4)
      orderDbManagerActor = context.actorSelection(paths(0))
      accountBalanceActor = context.actorSelection(paths(1))
      orderHistoryActor = context.actorSelection(paths(2))
      marketManagerActor = context.actorSelection(paths(3))

      startOrderRecovery()
  }

  def functional: Receive = functionalBase orElse LoggingReceive {

    case XGetBalanceAndAllowancesReq(addr, tokens) ⇒
      assert(addr == address)

      (for {
        managers ← Future.sequence(tokens.map(getTokenManager))
        _ = assert(tokens.size == managers.size)
        balanceAndAllowanceMap = tokens.zip(managers).toMap.map {
          case (token, manager) ⇒ token -> XBalanceAndAllowance(
            manager.getBalance(),
            manager.getAllowance(),
            manager.getAvailableBalance(),
            manager.getAvailableAllowance()
          )
        }
      } yield {
        XGetBalanceAndAllowancesRes(address, balanceAndAllowanceMap)
      }).pipeTo(sender)

    case XSubmitOrderReq(Some(xorder)) ⇒
      submitOrder(xorder).pipeTo(sender)

    case req: XCancelOrderReq ⇒
      if (manager.cancelOrder(req.id)) {
        marketManagerActor forward req
      } else {
        sender ! XCancelOrderRes(error = ERR_ORDER_NOT_EXIST)
      }

    case XAddressBalanceUpdated(_, token, newBalance) ⇒
      updateBalanceOrAllowance(token, newBalance, _.setBalance(_))

    case XAddressAllowanceUpdated(_, token, newBalance) ⇒
      updateBalanceOrAllowance(token, newBalance, _.setAllowance(_))

    case _ ⇒
  }

  private def submitOrder(xorder: XOrder): Future[XSubmitOrderRes] = {
    val order: Order = xorder
    for {
      _ ← getTokenManager(order.tokenS)
      _ ← getTokenManager(order.tokenFee) if order.amountFee > 0 && order.tokenS != order.tokenFee

      // Update the order's _outstanding field.
      orderHistoryRes ← (orderHistoryActor ? XGetOrderFilledAmountReq(order.id))
        .mapTo[XGetOrderFilledAmountRes]
      _ = log.debug(s"order history: orderHistoryRes")

      _order = order.withFilledAmountS(orderHistoryRes.filledAmountS)

      _ = log.debug(s"submitting order to AccountManager: ${_order}")
      successful = manager.submitOrder(_order)
      _ = log.info(s"successful: $successful")
      _ = log.info("orderPool updatdOrders: " + orderPool.getUpdatedOrders.mkString(", "))
      updatedOrders = orderPool.takeUpdatedOrdersAsMap()
      _ = assert(updatedOrders.contains(_order.id))
      order_ = updatedOrders(_order.id)
      xorder_ : XOrder = order_.copy(_reserved = None, _outstanding = None)
    } yield {
      if (successful) {
        log.debug(s"submitting order to market manager actor: $order_")
        marketManagerActor ! XSubmitOrderReq(Some(xorder_))
        XSubmitOrderRes(order = Some(xorder_))
      } else {
        val error = convertOrderStatusToErrorCode(order.status)
        XSubmitOrderRes(error = error)
      }
    }
  }

  private def convertOrderStatusToErrorCode(status: XOrderStatus): XErrorCode = status match {
    case INVALID_DATA ⇒ ERR_INVALID_ORDER_DATA
    case UNSUPPORTED_MARKET ⇒ ERR_INVALID_MARKET
    case CANCELLED_TOO_MANY_ORDERS ⇒ ERR_TOO_MANY_ORDERS
    case CANCELLED_DUPLICIATE ⇒ ERR_ORDER_ALREADY_EXIST
    case _ ⇒ ERR_UNKNOWN
  }

  private def getTokenManager(token: String): Future[AccountTokenManager] = {
    if (manager.hasTokenManager(token))
      Future.successful(manager.getTokenManager(token))
    else for {
      res ← (accountBalanceActor ? XGetBalanceAndAllowancesReq(address, Seq(token)))
        .mapTo[XGetBalanceAndAllowancesRes]
      tm = new AccountTokenManagerImpl(token, 1000)
      ba: BalanceAndAllowance = res.balanceAndAllowanceMap(token)
      _ = tm.setBalanceAndAllowance(ba.balance, ba.allowance)
      tokenManager = manager.addTokenManager(tm)
    } yield tokenManager
  }

  private def updateBalanceOrAllowance(
    token: String,
    amount: BigInt,
    method: (AccountTokenManager, BigInt) ⇒ Unit
  ) = for {
    tm ← getTokenManager(token)
    _ = method(tm, amount)
    updatedOrders = orderPool.takeUpdatedOrders()
  } yield {
    updatedOrders.foreach { order ⇒
      order.status match {
        case CANCELLED_LOW_BALANCE | CANCELLED_LOW_FEE_BALANCE ⇒
          marketManagerActor ! XCancelOrderReq(order.id)

        case PENDING ⇒
          //allowance的改变需要更新到marketManager
          marketManagerActor ! XSubmitOrderReq(Some(order))

        case status ⇒
          log.error(s"unexpected order status caused by balance/allowance upate: $status")
      }
    }
  }

  protected def recoverOrder(xorder: XOrder): Future[Any] = submitOrder(xorder)

}

