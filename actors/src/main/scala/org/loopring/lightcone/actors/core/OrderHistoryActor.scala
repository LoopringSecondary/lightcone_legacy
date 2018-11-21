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
import org.loopring.lightcone.proto.actors._

import scala.concurrent._

object OrderHistoryActor {
  val name = "order_history"
}

// TODO(hongyu): implement this class.
// 该类是不是需要将history保存在数据库，从数据库获取？稍后再实现
class OrderHistoryActor()(
    implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor
  with ActorLogging {

  def receive: Receive = LoggingReceive {
    case XGetOrderFilledAmountReq(orderId) ⇒ //从数据库获取
    case XPersistOrderHistoryReq           ⇒ //保存在数据库
  }

}
