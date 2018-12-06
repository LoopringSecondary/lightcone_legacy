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

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.util.Timeout
import akka.pattern._
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.proto.actors._

import scala.concurrent.{ ExecutionContext, Future }

object OrdersRecoveryActor {
  val name = "orders_recovery"
}

class OrdersRecoveryActor()(
    implicit
    val ec: ExecutionContext,
    val timeout: Timeout,
    actors: Lookup[ActorRef]
)
  extends Actor
  with ActorLogging {

  private def orderEntryActor: ActorRef = actors.get(OrderEntryActor.name)
  private def accountManagerActor: ActorRef = actors.get(AccountManagerActor.name)

  /** 1、重启时，读取并返回所有的有效订单
   *  2、
   */
  //todo:job合并有难度，而且不是业务必须的，可以稍后作为优化项
  def receive: Receive = {
    case req: XRecoverOrdersReq ⇒ for {
      res ← (orderEntryActor ? req).mapTo[XRecoverOrdersRes]
      _ ← Future.sequence(res.orders.map {
        order ⇒ accountManagerActor ? XSubmitOrderReq() //todo:整理proto的返回值等, 并且future的数量需要控制
      })
    } yield Unit
  }

}
