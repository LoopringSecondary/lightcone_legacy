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

package io.lightcone.relayer.socketio

import com.corundumstudio.socketio._
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.data.SocketIOSubscription
import org.slf4s.Logging

abstract class SocketIONotifier[R]() extends Logging {

  val name: String

  def shouldNotifyClient(
      subscription: R,
      event: SocketIOSubscription.Response
    ): Boolean

  def wrapClient(
      client: SocketIOClient,
      subscription: SocketIOSubscription
    ): SocketIOSubscriber[R]

  def isSubscriptionValid(subscription: SocketIOSubscription): Boolean

  private var clients = Seq.empty[SocketIOSubscriber[R]]

  def notifyEvent(
      event: SocketIOSubscription.Response,
      eventName: String
    ): Unit = {
    clients = clients.filter(_.client.isChannelOpen)
    val e = transformEvent(event)
    val targets = clients
      .filter(client => shouldNotifyClient(client.subscription, e))

    targets.foreach(_.sendEvent(eventName, e))

    log.debug(s"socketio notify: $e to ${targets.size} subscribers")
  }

  def onData(
      client: SocketIOClient,
      subscription: SocketIOSubscription
    ): Boolean = {
    val isValid = isSubscriptionValid(subscription)
    if (isValid) {
      val wrapped = wrapClient(client, subscription)
      clients = wrapped +: clients.filterNot(_ == wrapped)
    }
    isValid
  }

  // Override this method to change the event. Normally we should not do this.
  def transformEvent(
      event: SocketIOSubscription.Response
    ): SocketIOSubscription.Response = event
}
