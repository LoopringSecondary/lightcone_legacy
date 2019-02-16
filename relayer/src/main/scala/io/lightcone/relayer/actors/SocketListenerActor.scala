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

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.google.inject.Inject
import io.lightcone.relayer.base.{DeployedAsSingleton, Lookup}
import io.lightcone.relayer.data.GetBalanceAndAllowances
import io.lightcone.relayer.socket.Listeners._

import scala.concurrent.ExecutionContext

object SocketListenerActor extends DeployedAsSingleton {
  val name = "socket_listener"

  def start(
      implicit
      system: ActorSystem,
      ec: ExecutionContext,
      timeout: Timeout,
      listeners: Lookup[WrappedDataListener[_]],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new SocketListenerActor()))
  }
}

class SocketListenerActor @Inject()(
    implicit
    val system: ActorSystem,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val listeners: Lookup[WrappedDataListener[_]])
    extends Actor {

  def receive: Receive = {
    case req: GetBalanceAndAllowances.Res =>
      listeners.get(BalanceListener.eventName).dataChanged(req)
  }
}
