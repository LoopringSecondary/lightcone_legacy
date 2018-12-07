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
import akka.pattern._
import akka.util.Timeout
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.persistence.OrdersDalActor
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core.XOrderStatus

import scala.concurrent.ExecutionContext

object OrderEntryActor {
  val name = "order_entry"
}

/** 用户请求core的入口
 */
class OrderEntryActor()(
    implicit
    val ec: ExecutionContext,
    val timeout: Timeout,
    actors: Lookup[ActorRef]
)
  extends Actor
  with ActorLogging {

  private def ordersDalActor: ActorRef = actors.get(OrdersDalActor.name)
  private def accountManagerActor: ActorRef = actors.get(AccountManagerShardingActor.name)

  //todo：重新整理保存order的层次与结构
  /** 1、提交订单
   *  2、取消订单
   */
  def receive: Receive = {
    case req: XSubmitRawOrderReq ⇒ //提交订单
      (for {
        _ ← ordersDalActor ? req //保存到数据库
        res ← accountManagerActor ? XAccountMsgWrap(
          req.getRawOrder.owner,
          XAccountMsgWrap.Data.SubmitOrder(XSubmitOrderReq(Some(req.getRawOrder)))
        ) //然后发送到accountmanager
      } yield res) pipeTo sender
    case req: XCancelOrderReq ⇒ //取消订单
      (for {
        order ← (ordersDalActor ? XGetOrderByHashReq(req.id))
          .mapTo[XGetOrderByHashRes]
        res ← accountManagerActor ? XAccountMsgWrap(
          order.getOrder.owner,
          XAccountMsgWrap.Data.CancelOrder(req.copy(status = XOrderStatus.STATUS_CANCELLED_BY_USER))
        )
      } yield res) pipeTo sender
  }

}
