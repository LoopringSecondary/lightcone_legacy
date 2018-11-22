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
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.deployment.XActorDependencyReady
import org.loopring.lightcone.actors.data._

import scala.concurrent.ExecutionContext

object OrderDbManagerActor {
  val name = "order_db_manager"
}

// TODO(hongyu): implement this actor to support AMA and MMA.
class OrderDbManagerActor(databaseManager: OrderDatabaseManager)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor
  with ActorLogging {

  def receive: Receive = LoggingReceive {
    case _ ⇒

  }

}

// TODO(litao): move this to auxiliary sub project and implement the logic

trait OrderDatabaseManager {

  def validateOrder(xorder: XRawOrder): Option[XErrorCode]

  def saveOrder(xorder: XRawOrder): XRawOrder

  def getOrder(orderId: String): Option[XRawOrder]

  def getOrders(orderIds: Seq[String]): Seq[XRawOrder]

  def getOrdersForRecovery(since: Long, num: Int, owner: Option[String]): Seq[XRawOrder]

  def updateOrderStatus(actualState: XOrderState, status: XOrderStatus): Boolean
}
