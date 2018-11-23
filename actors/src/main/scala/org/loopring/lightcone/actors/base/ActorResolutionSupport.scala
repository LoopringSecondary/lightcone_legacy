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

trait ActorResolutionSupport {
  s: Actor with ActorLogging ⇒

  private var dependencyMap = Map.empty[String, ActorRef ⇒ Unit]

  def resolveActor(name: String, method: ActorRef ⇒ Unit) = {
    dependencyMap += name -> method
  }

  def receive: Receive = LoggingReceive {
    case XActorDependencyReady ⇒
      dependencyMap.foreach {
        case (k, _) ⇒ context.actorSelection(k.toString) ! Identify(k)
      }

    case ActorIdentity(path: String, Some(ref)) ⇒
      dependencyMap.get(path) match {
        case Some(assign) ⇒
          assign(ref)
          dependencyMap -= path
        case None ⇒ log.error(s"not expected: $path")
      }

      if (dependencyMap.isEmpty) {
        afterActorResolution()
      }

    case ai: ActorIdentity ⇒
      log.error(s"unable to process request $ai")
      context.stop(self)
  }

  def afterActorResolution(): Unit
}
