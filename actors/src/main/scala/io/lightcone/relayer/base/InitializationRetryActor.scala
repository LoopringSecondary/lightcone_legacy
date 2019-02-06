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

package io.lightcone.relayer.base

import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import io.lightcone.proto.Notify

abstract class InitializationRetryActor
  extends Actor
  with Stash
  with ActorLogging {
  implicit val ec: ExecutionContext

  def initialize(): Future[Unit] = Future.successful {
    becomeReady()
  }

  def ready: Receive

  def becomeReady() = {
    context.become(ready)
    unstashAll()
  }

  // TODO:正式部署需要适当放大该参数
  val initializationMaxRetries = 120
  val initializationDelayFactor = 1
  val initializationDelaySeconds = 2
  val initializationDelaySecondsMax = 60
  val selfInitialize = true

  private var _initializationDelay = initializationDelaySeconds
  private var _initializationRetries = 0

  if (selfInitialize) {
    self ! Notify("initialize")
  }

  def receive: Receive = {
    case Notify("initialize", _) =>
      log.info(
        s"initializing ${self.path}, attempt ${_initializationRetries}...")

      initialize() onComplete {
        case Success(res) =>
          log.info(s"---### ${self.path} initialization done, ")

        case Failure(e) =>
          if (_initializationRetries >= initializationMaxRetries) {
            log.error(
              s"---### ${self.path} initialization failed after ${initializationMaxRetries} retries: ${e.printStackTrace()}")
            throw e
          } else {
            log.warning(
              s"---### ${self.path} initialization failed, will  retry in ${_initializationDelay} seconds, ${e.printStackTrace()} ")

            context.system.scheduler
              .scheduleOnce(
                _initializationDelay.seconds,
                self,
                Notify("initialize"))

            _initializationRetries += 1
            _initializationDelay = Math.min(
              initializationDelaySecondsMax,
              _initializationDelay * initializationDelayFactor)
          }
      }

    case _ =>
      stash()
  }
}
