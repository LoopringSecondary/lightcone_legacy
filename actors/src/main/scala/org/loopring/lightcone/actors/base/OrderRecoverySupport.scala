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

package org.loopring.lightcone.actors.base

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core.XOrderStatus._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.deployment._

import scala.concurrent._

trait OrderRecoverySupport {
  self: Actor with ActorLogging ⇒

  implicit val ec: ExecutionContext
  implicit val timeout: Timeout

  val skipRecovery: Boolean // for testing purpose
  val recoverBatchSize: Int
  val ownerOfOrders: Option[String]
  private var batch = 1

  protected var orderDatabaseAccessActor: ActorRef

  protected def recoverOrder(xorder: XOrder): Future[Any]

  protected def functional: Receive

  protected def startOrderRecovery() = {
    if (skipRecovery) {
      context.become(functional)
    } else {
      context.become(recovering)
      log.info(s"actor recovering started: ${self.path}")
      self ! XRecoverOrdersReq(ownerOfOrders.getOrElse(null), 0L, recoverBatchSize)
    }
  }

  def recovering: Receive = {

    case XRecoverOrdersRes(xraworders) ⇒
      log.info(s"recovering batch $batch (size = ${xraworders.size})")
      batch += 1

      val xorders = xraworders.map(convertXRawOrderToXOrder)
      for {
        _ ← Future.sequence(xorders.map(recoverOrder))
        lastUpdatdTimestamp = xorders.lastOption.map(_.updatedAt).getOrElse(0L)
        recoverEnded = lastUpdatdTimestamp == 0 || xorders.size < recoverBatchSize
        _ = context.become(functional) if recoverEnded
        _ = orderDatabaseAccessActor ! XRecoverOrdersReq(
          ownerOfOrders.getOrElse(null),
          lastUpdatdTimestamp,
          recoverBatchSize
        )
      } yield Unit

    case msg ⇒
      log.debug(s"ignored msg during recovery: ${msg.getClass.getName}")
  }

  def functionalBase: Receive = LoggingReceive {

    case XRecoverOrdersRes(xraworders) ⇒
      log.info(s"recovering last batch (size = ${xraworders.size})")
      val xorders = xraworders.map(convertXRawOrderToXOrder)
      Future.sequence(xorders.map(recoverOrder))
  }
}
