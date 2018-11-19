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
import org.loopring.lightcone.actors.base.data._
import org.loopring.lightcone.core.base.DustOrderEvaluator
import org.loopring.lightcone.proto.actors.{ CancelOrderReq, SubmitOrderReq }

import scala.concurrent.ExecutionContext

class AccountManagerActor(
  manager: AccountManager
)(
  implicit
  ec: ExecutionContext,
  timeout: Timeout,
  routers: Routers,
  orderPool: AccountOrderPool,
  dustEvaluator: DustOrderEvaluator
)
  extends Actor
  with ActorLogging {

  def receive: Receive = LoggingReceive {
    case req: SubmitOrderReq ⇒
      val order = req.getOrder
      if (!manager.hasTokenManager(order.tokenS)) {
        val tm = new AccountTokenManagerImpl(order.tokenS)
        manager.addTokenManager(tm)
      }
      if (manager.submitOrder(order)) {
        Routers.marketManagerActor() ! order
      }
    case req: CancelOrderReq ⇒
      if (manager.cancelOrder(req.id)) {
        Routers.marketManagerActor() ! req
      }
  }

}
