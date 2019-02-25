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
import com.corundumstudio.socketio.listener.DataListener
import io.lightcone.relayer.data.SocketIOSubscription

class RelayerNotifier(notifiers: SocketIONotifier[_]*)
    extends DataListener[SocketIOSubscription] {

  val eventName: String = "lightCone"

  def onData(
      client: SocketIOClient,
      subscription: SocketIOSubscription,
      ackSender: AckRequest
    ): Unit = {

    val updatedNotifiers = notifiers.filter(_.onData(client, subscription))

    if (ackSender.isAckRequested) {
      if (updatedNotifiers.nonEmpty) {
        val eventNames = updatedNotifiers.map(_.name).mkString(",")
        ackSender.sendAckData(
          SocketIOSubscription
            .Ack(
              message = s"$subscription successfully subscribed $eventNames:"
            )
        )
      } else {
        ackSender.sendAckData(
          SocketIOSubscription
            .Ack(
              message =
                s"$subscription  successfully subscribed none of the events"
            )
        )
      }
    }
  }

  def notifyEvent(event: SocketIOSubscription.Response): Unit =
    notifiers.foreach(_.notifyEvent(event, eventName))
}
