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
import org.loopring.lightcone.auxiliary.order.OrderDatabaseManager
import org.loopring.lightcone.proto.actors._

import scala.concurrent._

object OrderDatabaseAccessActor {
  val name = "order_db_access"
}

// TODO(hongyu): implement this actor to support AMA and MMA.
class OrderDatabaseAccessActor(databaseManager: OrderDatabaseManager)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor
  with ActorLogging {

  protected var accountManagerRouter: ActorSelection = _

  def receive: Receive = LoggingReceive {
    case XRecoverOrdersReq(address, updatedSince, num) ⇒
      for {
        orders ← databaseManager.getOrderByHash(
          "order_hash"
        )
        // _ = sender ! XRecoverOrdersRes(orders)
      } yield Unit

    case XSubmitRawOrderReq(Some(xraworder)) ⇒
      databaseManager.validateOrder(xraworder) match {
        case Left(error) ⇒
          assert(error != XErrorCode.ERR_OK)
          sender ! XSubmitOrderRes(error = error)

        case Right(xraworder) ⇒
          for {
            xraworder ← databaseManager.saveOrUpdate(xraworder)
            // TODO compile failed
            //            xorder: XOrder = xraworder
            //            _ = accountManagerRouter forward XSubmitOrderReq(Some(xorder))
          } yield Unit
      }

    case XUpdateOrderStateAndStatusReq(Some(actualState), status) ⇒
    // TODO 可以使用 saveOrUpdate
    //      for {
    //        result ← databaseManager.updateOrderStateAndStatus(actualState, status)
    //        _ = sender ! XUpdateOrderStateAndStatusRes(result)
    //      } yield Unit

    // case XGetOrders(orderIds) =>

  }

}
