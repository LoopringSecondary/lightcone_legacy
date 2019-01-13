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
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

abstract class InitializationFaultTolerentActor
    extends Actor
    with Stash
    with ActorLogging {
  implicit val ec: ExecutionContext

  def initialize(): Future[Unit]

  def initialized: Receive

  val initializationMaxRetries = 20
  val initializationDelayFactor = 2
  val initializationDelaySeconds = 10
  val initializationDelaySecondsMax = 60
  val selfInitialize = true

  private var _initializationDelay = initializationDelaySeconds
  private var _initializationRetries = 0

  if (selfInitialize) {
    self ! "start"
  }

  def receive: Receive = {
    case "start" =>
      initialize() onComplete {
        case Success(res) =>
          log.info(
            s"---->>> ${self.path.name} initialization done, ",
            s"entered `initialized` state"
          )
          context.become(initialized)

        case Failure(e) =>
          if (_initializationRetries >= initializationMaxRetries) {
            log.error(
              s"---->>> ${self.path.name} initialization failed ",
              s"after ${initializationMaxRetries} retries: ",
              e
            )
          } else {
            log.warning(
              s"---->>> ${self.path.name} initialization failed, ",
              s"will  retry in ${_initializationDelay} seconds"
            )

            context.system.scheduler
              .scheduleOnce(_initializationDelay.seconds, self, "start")

            _initializationRetries += 1
            _initializationDelay = Math.min(
              initializationDelaySecondsMax,
              _initializationDelay * initializationDelayFactor
            )
          }
      }

    case _ => stash()
  }
}
