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

package org.loopring.lightcone.actors.actor

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import org.loopring.lightcone.actors.routing.Routers
import org.loopring.lightcone.proto.actors.{ ErrorCode, GetFilledAmountReq, GetFilledAmountRes, SubmitOrderReq, SubmitOrderRes, UpdateFilledAmountReq }
import org.loopring.lightcone.core._
import org.loopring.lightcone.proto.deployment.OrderFillSettings
import org.loopring.lightcone.actors.base

import scala.concurrent.{ ExecutionContext, Future }

object OrderFillHistoryActor
  extends base.Deployable[OrderFillSettings] {
  val name = "order_fill_history_actor"

  def getCommon(s: OrderFillSettings) =
    base.CommonSettings(None, s.roles, s.instances)
}

class OrderFillHistoryActor()(
  implicit
  ec: ExecutionContext,
  timeout: Timeout)
  extends Actor
  with ActorLogging {

  def receive() = LoggingReceive {
    case req: GetFilledAmountReq ⇒ Routers.ethereumAccessActor forward req
    case req: UpdateFilledAmountReq ⇒ Routers.ethereumAccessActor forward req

    case SubmitOrderReq(orderOpt) if orderOpt.isEmpty ⇒
      log.error(s"SubmitOrderReq order with empty order")

    case SubmitOrderReq(orderOpt) if orderOpt.nonEmpty ⇒
      val order = orderOpt.get.toPojo

      for {
        filledAmountSMap ← getFilledAmountAsFuture(Seq(order.id))
        filledAmountS: BigInt = filledAmountSMap(order.id)
        updated = if (filledAmountS == 0) order else {
          val outstanding = order.outstanding.scaleBy(
            Rational(order.amountS - filledAmountS, order.amountS))
          order.copy(_outstanding = Some(outstanding))
        }
      } yield {
        if (updated.outstanding.amountS <= 0) {
          sender ! SubmitOrderRes(ErrorCode.ORDER_INVALID_AMOUNT_S, None)
        } else {
          Routers.orderManagingActor forward SubmitOrderReq(Some(updated.toProto))
        }
      }
  }

  def getFilledAmountAsFuture(orderIds: Seq[String]): Future[Map[String, BigInt]] = {
    (Routers.ethereumAccessActor ? GetFilledAmountReq(orderIds))
      .mapTo[GetFilledAmountRes]
      .map(_.filledAmountSMap)
  }
}
