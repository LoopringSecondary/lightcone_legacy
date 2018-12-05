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

package org.loopring.lightcone.actors.persistence

import akka.actor._
import akka.event.LoggingReceive
import akka.util.Timeout
import akka.pattern._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.persistence.dals.OrderStateDal
import org.loopring.lightcone.proto.core.{ XOrderPersState, XOrderStatus }
import org.loopring.lightcone.proto.persistence.XPersistenceError

import scala.concurrent._

object OrderStateActor {
  val name = "order_state"
}

// TODO(hongyu): implement this class. 根据变更可能需要从数据库读取
class OrderStateActor(
    orderStateDal: OrderStateDal
)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor
  with ActorLogging {

  def receive: Receive = LoggingReceive {
    case XGetOrderFilledAmountReq(hash) ⇒ //从以太坊读取
      //todo: 测试deploy
      sender ! XGetOrderFilledAmountRes(hash, BigInt(0))
    case XRecoverOrdersReq(address, marketIdOpt, updatedSince, num) ⇒
      val tokenes = (marketIdOpt match {
        case Some(marketId) ⇒ Set(marketId.primary, marketId.secondary)
        case None           ⇒ Seq.empty[String]
      }).toSet
      (for {
        orders ← orderStateDal.getOrdersByUpdatedAt(
          num = num,
          statuses = Set(XOrderStatus.STATUS_NEW, XOrderStatus.STATUS_PENDING),
          tokenSSet = tokenes,
          tokenBSet = tokenes,
          owners = if ("" != address) Set(address) else Set.empty,
          updatedSince = Some(updatedSince)
        )
      } yield XRecoverOrdersRes(orders)) pipeTo sender
    //TODO hongyu:改成saveOrUpdate
    case XUpdateOrderStateReq(hash, stateOpt, changeUpdatedAtField) ⇒
      (stateOpt match {
        case Some(state) ⇒ orderStateDal.saveOrUpdate(XOrderPersState())
        case None        ⇒ Future.successful(Left(XPersistenceError.PERS_ERR_INVALID_DATA))
      }) pipeTo sender
    case XUpdateOrderStatusReq(hash, status, changeUpdatedAtField) ⇒
      orderStateDal.updateOrderStatus(hash, status, changeUpdatedAtField) pipeTo sender
  }

}
