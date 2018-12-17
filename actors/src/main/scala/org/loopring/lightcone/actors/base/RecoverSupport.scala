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
import org.loopring.lightcone.proto._
import scala.concurrent.duration._

import scala.collection.mutable.Queue
import scala.concurrent._

trait RecoverSupport extends Actor with ActorLogging {

  implicit val ec: ExecutionContext
  implicit val timeout: Timeout
  val actors: Lookup[ActorRef]
  val skipRecovery: Boolean
  val recoverActorName: String

  def recoverOrder(xraworder: XRawOrder): Future[Any]
  def generateRecoveryRequest(): XRecoverReq

  private var processed = 0
  private var cancellable: Option[Cancellable] = None

  protected def requestRecovery() = {
    if (skipRecovery) {
      log.warning(s"actor recovering skipped: ${self.path}")
    } else {
      context.become(recovering)
      log.debug(s"actor recovering started: ${self.path}")
      actors.get(recoverActorName) ! generateRecoveryRequest() // TODO
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    requestRecovery()
  }

  def recoverOrders(xraworders: Seq[XRawOrder]) = {
    log.debug(s"recovering next ${xraworders.size} orders")
    Future.sequence(xraworders.map(recoverOrder))
  }

  def recovering: Receive = {
    case XRecoverRes(xraworders) =>
      // cancel the previous auto-cancel scheduling
      cancellable.foreach(_.cancel)
      cancellable = None
      val size = xraworders.size
      log.debug(s"recovering next ${size} orders")
      processed += size

      recoverOrders(xraworders).map { _ =>
        // auto-cancel after one minute
        cancellable = Option(
          context.system.scheduler.scheduleOnce(1.minute, self, XRecoverEnded())
        )
      }

    case msg: XRecoverEnded =>
      context.become(receive)

    case msg =>
      log.debug(s"ignored msg during recovery: ${msg.getClass.getName}")
    //      stash() //恢复期间，暂时保存消息
  }

}
