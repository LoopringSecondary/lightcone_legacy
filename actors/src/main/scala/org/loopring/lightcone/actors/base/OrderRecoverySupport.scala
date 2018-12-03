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
import akka.util.Timeout
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core.XMarketId

import scala.concurrent._

case class OrderRecoverySettings(
    skipRecovery: Boolean,
    batchSize: Int,
    ownerOfOrders: Option[String] = None,
    marketId: Option[XMarketId] = None
)

trait OrderRecoverySupport {
  actor: Actor with ActorLogging ⇒

  implicit val ec: ExecutionContext
  implicit val timeout: Timeout

  val recoverySettings: OrderRecoverySettings
  private var processed = 0

  protected def ordersDalActor: ActorRef

  protected def recoverOrder(xorder: XOrder): Future[Any]

  protected def functional: Receive

  private var lastUpdatdTimestamp: Long = 0
  private var recoverEnded: Boolean = false
  private var xordersToRecover: Seq[XOrder] = Nil

  protected def startOrderRecovery() = {
    if (recoverySettings.skipRecovery) {
      log.info(s"actor recovering skipped: ${self.path}")
      context.become(functional)
    } else {
      context.become(recovering)
      log.info(s"actor recovering started: ${self.path}")
      val marketId = recoverySettings.marketId
      ordersDalActor ! XRecoverOrdersReq(
        recoverySettings.ownerOfOrders.orNull,
        if (marketId.isEmpty) null else marketId.get.primary,
        if (marketId.isEmpty) null else marketId.get.secondary,
        0L,
        recoverySettings.batchSize
      )
    }
  }

  def recovering: Receive = {

    case XRecoverOrdersRes(xraworders) ⇒
      val size = xraworders.size
      log.info(s"recovering next ${size} orders")
      processed += size

      xordersToRecover = xraworders.map(xRawOrderToXOrder).toList
      lastUpdatdTimestamp = xordersToRecover.lastOption.map(_.updatedAt).getOrElse(0L)
      recoverEnded = lastUpdatdTimestamp == 0 || xordersToRecover.size < recoverySettings.batchSize

      self ! XRecoverNextOrder()

    case _: XRecoverNextOrder ⇒
      xordersToRecover match {
        case head :: tail ⇒ for {
          _ ← recoverOrder(head)
        } yield {
          xordersToRecover = tail
          self ! XRecoverNextOrder()
        }

        case Nil ⇒
          if (!recoverEnded) {
            val marketId = recoverySettings.marketId
            ordersDalActor ! XRecoverOrdersReq(
              recoverySettings.ownerOfOrders.orNull,
              if (marketId.isEmpty) null else marketId.get.primary,
              if (marketId.isEmpty) null else marketId.get.secondary,
              lastUpdatdTimestamp,
              recoverySettings.batchSize
            )
          } else {
            log.info(s"recovering completed with $processed orders")
            context.become(functional)
          }
        case _ ⇒ log.info(s"not match xorders: ${xordersToRecover.getClass.getName}")
      }

    case msg ⇒
      log.debug(s"ignored msg during recovery: ${msg.getClass.getName}")
  }
}
