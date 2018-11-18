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
import akka.util.Timeout
import org.loopring.lightcone.actors.routing.Routers
import org.loopring.lightcone.proto.actors.{ XOrderStatus ⇒ _, _ }
import org.loopring.lightcone.core._
import org.loopring.lightcone.proto.deployment.OrderBookManagerSettings
import org.loopring.lightcone.actors.base

import scala.concurrent.ExecutionContext

object MarketManagingActor
  extends base.Deployable[OrderBookManagerSettings] {
  val name = "market_managing_actor"

  def getCommon(s: OrderBookManagerSettings) =
    base.CommonSettings(None, s.roles, 1)
}

class MarketManagingActor(
    manager: MarketManager
)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor
  with ActorLogging {
  var latestGasPrice = 0l

  def receive() = LoggingReceive {
    case SubmitOrderReq(Some(order)) ⇒
      order.status match {
        case XOrderStatus.PENDING | XOrderStatus.NEW ⇒
          val res = manager.submitOrder(order.toPojo)
          val rings = res.rings map (_.toProto)
          if (rings.nonEmpty) {
            Routers.ringSubmitActor ! SubmitRingReq(rings = rings)
          }
        case _ ⇒
          manager.deleteOrder(order.toPojo)
      }

    case updatedGasPrce: UpdatedGasPrice ⇒
      if (latestGasPrice > updatedGasPrce.gasPrice) {
        val res = manager.triggerMatch()
        Routers.ringSubmitActor ! SubmitRingReq(rings = res.rings map (_.toProto))
      }
      latestGasPrice = updatedGasPrce.gasPrice

    case req: RingExecutedRes ⇒
      val ringOpt = req.ring.map(_.toPojo)
      ringOpt.foreach(manager.deletePendingRing)
  }

}
