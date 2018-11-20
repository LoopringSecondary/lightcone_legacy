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
import akka.util.Timeout
import akka.pattern.{ ask, pipe }
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.deployment._
import org.loopring.lightcone.actors.data._

import scala.concurrent._

import XOrderStatus._
import XErrorCode._

object AccountManagerActor {
  val name = "account_manager"
}

class AccountManagerActor(address: String)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    dustEvaluator: DustOrderEvaluator
)
  extends Actor
  with ActorLogging {

  implicit val orderPool = new AccountOrderPoolImpl()
  val manager = AccountManager.default

  private var accountBalanceActor: ActorSelection = _
  private var marketManagerActor: ActorSelection = _

  def receive: Receive = LoggingReceive {
    case ActorDependencyReady(paths) ⇒
      log.info(s"actor dependency ready: $paths")
      assert(paths.size == 2)
      accountBalanceActor = context.actorSelection(paths(0))
      marketManagerActor = context.actorSelection(paths(1))
      context.become(functional)
  }

  // TODO(hongyu): handle the following messages:
  // 没理解这个message的用途
  // - XQueryBalance: (this should query AccountBalanceActor first,
  //   then query unreserved allowance)
  def functional: Receive = LoggingReceive {

    case XSubmitOrderReq(Some(xorder)) ⇒
      (for {
        _ ← getTokenManager(xorder.tokenS)
        _ ← getTokenManager(xorder.tokenFee)
        order: Order = xorder
        _ = log.debug(s"submitting order to AccountManager: $order")
        successful = manager.submitOrder(order)
        _ = log.debug("orderPool updatdOrders: " + orderPool.getUpdatedOrders)
        updatedOrders = orderPool.takeUpdatedOrdersAsMap()
        //todo: 该处获取的updatedOrders为空，但是我没看明白UpdatedOrdersTracing是如何使用的
        orderOpt = updatedOrders.get(order.id)
        _ = assert(orderOpt.nonEmpty)
        order_ = orderOpt.get
        xorder_ : XOrder = xorder
      } yield {

        if (successful) {
          log.debug(s"submitting order to market manager actor: $order_")
          marketManagerActor ! XSubmitOrderReq(Some(xorder_))
          XSubmitOrderRes(order = Some(xorder_))
        } else {
          val error = convertOrderStatusToErrorCode(order.status)
          XSubmitOrderRes(error = error)
        }
      }).pipeTo(sender)

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

    case req: XQueryBalance ⇒
    case _                  ⇒
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
      res ← (accountBalanceActor ? XGetBalanceAndAllowancesReq("addr", Seq(token)))
        .mapTo[XGetBalanceAndAllowancesRes]
      tm = new AccountTokenManagerImpl(token, 1000)
      ba: BalanceAndAllowance = res.balanceAndAllowanceMap(token)
      _ = tm.setBalanceAndAllowance(ba.balance, ba.allowance)
      _ = manager.addTokenManager(tm)
    } yield {
      manager.getTokenManager(token)
    }
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
        case s ⇒
          log.error(s"unexpected order status caused by balance/allowance upate: $s")
      }
    }
  }
}
