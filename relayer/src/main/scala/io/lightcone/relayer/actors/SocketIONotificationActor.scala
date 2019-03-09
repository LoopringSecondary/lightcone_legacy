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
import io.lightcone.core.RawOrder
import io.lightcone.relayer.base._
import io.lightcone.relayer.socketio._
import io.lightcone.relayer.RpcDataLinters._
import io.lightcone.relayer.data.AccountUpdate

import scala.concurrent.ExecutionContext

object SocketIONotificationActor extends DeployedAsSingleton {
  val name = "socketio_notifier"

  def start(
      implicit
      system: ActorSystem,
      ec: ExecutionContext,
      timeout: Timeout,
      notifer: SocketIONotifier,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new SocketIONotificationActor()))
  }
}

class SocketIONotificationActor @Inject()(
    implicit
    val system: ActorSystem,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val notifer: SocketIONotifier)
    extends Actor {

  def receive: Receive = {
    case order: RawOrder =>
      notifer.notifyEvent(rawOrderLinter.lint(order))

    case accountUpdate: AccountUpdate =>
      accountBalanceUpdateLinter.lint(accountUpdate)

    case event: AnyRef => notifer.notifyEvent(event)
  }
}
