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

import akka.actor.{ Actor, ActorLogging }
import akka.event.LoggingReceive
import akka.util.Timeout
import org.loopring.lightcone.actors.Routers
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base.DustOrderEvaluator
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core.XOrderStatus._
import org.loopring.lightcone.actors.conversions._

import scala.concurrent.ExecutionContext

class AccountManagerActor()(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    routers: Routers,
    dustEvaluator: DustOrderEvaluator
)
  extends Actor
  with ActorLogging {

  implicit val orderPool = new AccountOrderPoolImpl()
  val manager = AccountManager.default()
  val marketManagerActor = Routers.marketManagerActor()

  // TODO(hongyu): 需要处理下列事件：
  // 1. allowance变化
  // 2. balance变化
  def receive: Receive = LoggingReceive {

    case req: XSubmitOrderReq ⇒
      val order = req.getOrder
      if (!manager.hasTokenManager(order.tokenS)) {
        // TODO(dong): read from config
        val tm = new AccountTokenManagerImpl(order.tokenS, 1000)
        // TODO(dong): do it asyncly
        tm.setBalanceAndAllowance(0, 0)
        manager.addTokenManager(tm)
      }

      if (!manager.hasTokenManager(order.tokenFee)) {
        // TODO(dong): read from config
        val tm = new AccountTokenManagerImpl(order.tokenFee, 1000)
        // TODO(dong): do it asyncly
        tm.setBalanceAndAllowance(0, 0)
        manager.addTokenManager(tm)
      }

      val successful = manager.submitOrder(order)
      val updatedOrders = orderPool.takeUpdatedOrdersAsMap()
      val xOrder: XOrder = updatedOrders(order.id)

      if (successful) {
        log.debug(s"submitting order to market manager actor: $xOrder")
        marketManagerActor ! XSubmitOrderReq(Some(xOrder))
        sender ! XSubmitOrderRes(order = Some(xOrder))
      } else {
        val error = xOrder.status match {
          case INVALID_DATA ⇒ XErrorCode.INVALID_ORDER_DATA
          case UNSUPPORTED_MARKET ⇒ XErrorCode.INVALID_MARKET
          case CANCELLED_TOO_MANY_ORDERS ⇒ XErrorCode.TOO_MANY_ORDERS
          case CANCELLED_DUPLICIATE ⇒ XErrorCode.ORDER_ALREADY_EXIST
          case _ ⇒ XErrorCode.UNKNOWN_ERROR
        }
        sender ! XSubmitOrderRes(error = error)
      }

    case req: XCancelOrderReq ⇒
      if (manager.cancelOrder(req.id)) {
        marketManagerActor ! req
      }
  }

}
