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
import akka.util.Timeout
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.proto.actors._

import scala.collection.mutable.Queue
import scala.concurrent._

trait OrderRecoverySupport {
  actor: Actor with ActorLogging ⇒

  implicit val ec: ExecutionContext
  implicit val timeout: Timeout

  var recoverySettings: XOrderRecoverySettings = _
  private var processed = 0

  protected def ordersRecoveryActor: ActorRef

  protected def recoverOrder(xorder: XOrder): Future[Any]

  protected def functional: Receive

  private var lastUpdatdTimestamp: Long = 0
  private var recoverEnded: Boolean = false
  private var xordersToRecover = Queue.empty[XOrder]

  protected def startOrderRecovery(settings: XOrderRecoverySettings) = {
    recoverySettings = settings
    if (recoverySettings.skipRecovery) {
      log.info(s"actor recovering skipped: ${self.path}")
      context.become(functional)
    } else {
      context.become(recovering)
      log.info(s"actor recovering started: ${self.path}")
      ordersRecoveryActor ! XRecoverOrdersReq(
        recoverySettings.orderOwner,
        recoverySettings.marketId,
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

      xordersToRecover ++= xraworders.map(xRawOrderToXOrder)
      lastUpdatdTimestamp = xordersToRecover.lastOption.map(_.updatedAt).getOrElse(0L)
      recoverEnded = lastUpdatdTimestamp == 0 || xordersToRecover.size < recoverySettings.batchSize

      self ! XRecoverNextOrder()

    case _: XRecoverNextOrder ⇒
      //不为空，并且已经发送完成了，才会变为正常的接受订单
      if (xordersToRecover.isEmpty) {
        if (!recoverEnded) {
          ordersRecoveryActor ! XRecoverOrdersReq(
            recoverySettings.orderOwner,
            recoverySettings.marketId,
            lastUpdatdTimestamp,
            recoverySettings.batchSize
          )
        } else {
          log.info(s"recovering completed with $processed orders")
          context.become(functional)
        }
      } else {
        for {
          _ ← recoverOrder(xordersToRecover.dequeue())
        } yield {
          self ! XRecoverNextOrder()
        }
      }

    case msg ⇒
      log.debug(s"ignored msg during recovery: ${msg.getClass.getName}")
  }
}
