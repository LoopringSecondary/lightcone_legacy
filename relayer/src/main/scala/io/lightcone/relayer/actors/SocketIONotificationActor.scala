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
import io.lightcone.core._
import io.lightcone.persistence._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.socketio._

import scala.concurrent.ExecutionContext

object SocketIONotificationActor extends DeployedAsSingleton {
  val name = "socketio_notifier"

  def start(
      implicit
      system: ActorSystem,
      ec: ExecutionContext,
      timeout: Timeout,
      notifer: RelayerNotifier,
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
    val notifer: RelayerNotifier)
    extends Actor {

  def receive: Receive = {
    // events to deliver to socket.io clients must be generated here, not inside the listeners.
    //TODO(yadong)  需要把不展示到前端的字段清除
    case event: RawOrder =>
      val res = SocketIOSubscription.Response(resForOrder = Some(event))
      notifer.notifyEvent(res)
    case event: AccountUpdate =>
      val res = SocketIOSubscription.Response(resForAccount = Some(event))
      notifer.notifyEvent(res)
    case event: Activity =>
      val res = SocketIOSubscription.Response(resForActivity = Some(event))
      notifer.notifyEvent(res)
    case fill: Fill =>
      val res = SocketIOSubscription.Response(resForFill = Some(fill))
      notifer.notifyEvent(res)
    case event: MarketMetadata =>
      val res = SocketIOSubscription.Response(resForMarkets = Some(event))
      notifer.notifyEvent(res)
    case event: Orderbook.Update =>
      val res = SocketIOSubscription.Response(resForOrderbook = Some(event))
      notifer.notifyEvent(res)
    case event: TokenMetadata =>
      val res = SocketIOSubscription.Response(resForTokens = Some(event))
      notifer.notifyEvent(res)
    case event: AnyRef =>
      notifer.notifyEvent(SocketIOSubscription.Response())
  }
}
