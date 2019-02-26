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
import io.lightcone.relayer.data._
import org.slf4s.Logging
import com.corundumstudio.socketio.listener.DataListener

abstract class SocketIONotifier
    extends DataListener[SocketIOSubscription]
    with Logging {

  import SocketIOSubscription._

  val eventName = "lightcone"

  def generateNotification(
      evt: AnyRef,
      subscription: SocketIOSubscription
    ): Option[Notification]

  def isSubscriptionValid(subscription: SocketIOSubscription): Boolean

  private var clients = Seq.empty[SocketIOSubscriber]

  def notifyEvent(event: AnyRef): Unit = {
    clients = clients.filter(_.client.isChannelOpen)

    val targets = clients
      .map(client => client -> generateNotification(event, client.subscription))

    targets.foreach {
      case (client, Some(notification)) =>
        client.sendEvent(eventName, notification)
      case _ =>
    }

    log.debug(s"socketio notify: $event to ${targets.size} subscribers")
  }

  def onData(
      client: SocketIOClient,
      subscription: SocketIOSubscription,
      ackSender: AckRequest
    ) = {

    val isValid = isSubscriptionValid(subscription)

    // TODO
    if (ackSender.isAckRequested) {
      val ack =
        if (isValid) {
          SocketIOSubscription.Ack(
            message = s"$subscription successfully subscribed $eventName"
          )
        } else {
          SocketIOSubscription.Ack(message = s"invalid subscription")
        }
      ackSender.sendAckData(ack)
    }

    if (isValid) {
      val wrapped = new SocketIOSubscriber(client, subscription)
      clients = wrapped +: clients.filterNot(_ == wrapped)
    }
  }
}
