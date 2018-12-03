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
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.persistence._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.persistence.dals._

import scala.concurrent._

object OrdersDalActor {
  val name = "orders_dal"
}

// TODO(hongyu): remove databaseManager and use OrdersDal
class OrdersDalActor(
    ordersDal: OrdersDal,
    databaseManager: OrderDatabaseManager
)(
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
        orders ← databaseManager.getOrdersForRecovery(
          updatedSince, num, Option(address)
        )
        _ = sender ! XRecoverOrdersRes(orders)
      } yield Unit

    case XSubmitRawOrderReq(Some(xraworder)) ⇒
      databaseManager.validateOrder(xraworder) match {
        case Left(error) ⇒
          assert(error != XErrorCode.ERR_OK)
          sender ! XSubmitOrderRes(error = error)

        case Right(xraworder) ⇒
          for {
            xraworder ← databaseManager.saveOrder(xraworder)
            xorder: XOrder = xraworder
            _ = accountManagerRouter forward XSubmitOrderReq(Some(xorder))
          } yield Unit
      }

    case XUpdateOrderStateAndStatusReq(Some(actualState), status) ⇒
      for {
        result ← databaseManager.updateOrderStateAndStatus(actualState, status)
        _ = sender ! XUpdateOrderStateAndStatusRes(result)
      } yield Unit

    // case XGetOrders(orderIds) =>

  }

}

// TODO(litao): move this to persistence sub project and implement the logic
// and probably move XRawOrder definition to persistence.proto
trait OrderDatabaseManager {

  def validateOrder(xraworder: XRawOrder): Either[XErrorCode, XRawOrder]

  def saveOrder(xraworder: XRawOrder): Future[XRawOrder]

  def getOrdersForRecovery(since: Long, num: Int, owner: Option[String]): Future[Seq[XRawOrder]]

  def updateOrderStateAndStatus(actualState: XOrderState, status: XOrderStatus): Future[Boolean]

  // TODO(litao): design more flexibale order reading APIs
  def getOrder(orderId: String): Future[Option[XRawOrder]]

  def getOrders(orderIds: Seq[String]): Future[Seq[XRawOrder]]
}

