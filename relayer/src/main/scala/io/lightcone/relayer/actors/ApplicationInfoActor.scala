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

package io.lightcone.relayer.actors

import akka.actor._
import akka.util.Timeout
import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.lib.{NumericConversion, TimeProvider}
import io.lightcone.relayer.base.{DeployedAsSingleton, Lookup}
import io.lightcone.relayer.data.GetTime

import scala.concurrent.ExecutionContext

object ApplicationInfoActor extends DeployedAsSingleton {
  val name = "application_info"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new ApplicationInfoActor()))
  }
}

class ApplicationInfoActor @Inject()(
    implicit
    val system: ActorSystem,
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends Actor
    with Stash
    with ActorLogging {

  def receive: Receive = {

    case _: GetTime.Req =>
      sender ! GetTime.Res(
        timestamp = Some(
          NumericConversion.toAmount(BigInt(timeProvider.getTimeSeconds()))
        )
      )
  }

}
