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
import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import akka.cluster.sharding._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.base.DustOrderEvaluator
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.XErrorCode._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.serialization._
import scalapb.json4s.JsonFormat

object OrderRecoverCoordinator extends {
  val name = "order_recover_coordinator"
}

class OrderRecoverCoordinator(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dustEvaluator: DustOrderEvaluator)
    extends ActorWithPathBasedConfig(OrderRecoverCoordinator.name)
    with ActorLogging {

  val batchTimeout = selfConfig.getInt("batch-timeout-seconds")
  var activeBatches = Map.empty[ActorRef, XRecover.BatchAck]
  var pendingBatch = XRecover.Batch(batchId = 1)
  var batchTimer: Option[Cancellable] = None

  def receive: Receive = {

    case req: XRecover.Request =>
      cancelBatchTimer()

      val senderPath = Serialization.serializedActorPath(sender)

      activeBatches.find {
        case (k, v) => v.requestMap.contains(senderPath)
      }.foreach {
        case (orderRecoverActor, ack) =>
          val requestMap = ack.requestMap.filterNot(_._1 == senderPath)
          val updatedAck = ack.copy(requestMap = requestMap)
          activeBatches += orderRecoverActor -> updatedAck

          orderRecoverActor ! XRecover.Cancel(senderPath)
      }

      val requestMap = pendingBatch.requestMap + (senderPath -> req)
      pendingBatch = pendingBatch.copy(requestMap = requestMap)

      log.info(s"current pending batch recovery request: ${pendingBatch}")

      startBatchTimer()

    case ack: XRecover.BatchAck =>
      log.warning(s"""
      |>>>
      |>>> BATCH RECOVER STARTED:
      |>>> ${JsonFormat.toJsonString(ack)}
      |>>> """)

      activeBatches += sender -> ack

    case msg: XRecover.Finished if activeBatches.contains(sender) =>
      log.warning(s"""
      |>>>
      |>>> BATCH RECOVER FINISHED:
      |>>> ${JsonFormat.toJsonString(activeBatches(sender))}
      |>>> """)

      activeBatches -= sender

    case req: XRecover.Timeout =>
      if (pendingBatch.requestMap.nonEmpty) {
        actors.get(OrderRecoverActor.name) ! pendingBatch
        pendingBatch = XRecover.Batch(pendingBatch.batchId + 1)
      }
  }

  private def startBatchTimer() {
    if (batchTimer.isEmpty) {
      batchTimer = Some(
        context.system.scheduler
          .scheduleOnce(batchTimeout.seconds, self, XRecover.Timeout())
      )
    }
  }

  private def cancelBatchTimer() {
    batchTimer.foreach(_.cancel)
    batchTimer = None
  }

}
