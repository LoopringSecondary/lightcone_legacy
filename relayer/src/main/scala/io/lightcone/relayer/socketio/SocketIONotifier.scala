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
import io.lightcone.core.{ErrorCode, ErrorException}
import io.lightcone.lib.Address
import io.lightcone.relayer.data._
import org.slf4s.Logging

abstract class SocketIONotifier
    extends DataListener[SocketIOSubscription]
    with Logging {

  import SocketIOSubscription._

  val eventName = "lightcone"

  def generateNotification(
      evt: AnyRef,
      subscription: SocketIOSubscription
    ): Option[Notification]

  def checkSubscriptionValidation(subscription: SocketIOSubscription): Unit

  private var clients = Seq.empty[SocketIOSubscriber]

  def notifyEvent(event: AnyRef): Unit = {
    clients = clients.filter(_.client.isChannelOpen)

    val targets = clients.map { client =>
      client -> generateNotification(event, client.subscription)
    }

    targets.foreach {
      case (client, Some(notification)) =>
        client.sendEvent(eventName, notification)
      case _ =>
    }

    log.debug(
      s"socketio notify: $event to ${targets.count(_._2.isDefined)} subscribers"
    )
  }

  def normalizeSubscription(
      subscription: SocketIOSubscription
    ): SocketIOSubscription =
    subscription.copy(
      paramsForAccounts = subscription.paramsForAccounts.map(
        params =>
          params.copy(
            addresses = params.addresses.map(Address.normalize),
            tokens = params.tokens.map(Address.normalize)
          )
      ),
      paramsForActivities = subscription.paramsForActivities.map(
        params =>
          params.copy(addresses = params.addresses.map(Address.normalize))
      ),
      paramsForFills = subscription.paramsForFills.map { params =>
        params.copy(
          address =
            if (params.address == null || params.address.isEmpty) ""
            else Address.normalize(params.address),
          marketPair = params.marketPair.map(_.normalize())
        )
      },
      paramsForOrderbook = subscription.paramsForOrderbook.map(
        params =>
          params.copy(
            marketPair = params.marketPair.map(_.normalize())
          )
      ),
      paramsForOrders = subscription.paramsForOrders.map(
        params =>
          params.copy(
            addresses = params.addresses.map(Address.normalize),
            marketPair = params.marketPair.map(_.normalize())
          )
      ),
      paramsForInternalTickers = subscription.paramsForInternalTickers.map(
        params =>
          params.copy(
            marketPair = params.marketPair.map(_.normalize())
          )
      )
    )

  def onData(
      client: SocketIOClient,
      subscription: SocketIOSubscription,
      ackSender: AckRequest
    ) = {
    val ackData = try {
      checkSubscriptionValidation(subscription)
      val wrapped =
        new SocketIOSubscriber(client, normalizeSubscription(subscription))
      clients = wrapped +: clients.filterNot(_ == wrapped)
      SocketIOSubscription.Ack(
        message = s"$subscription successfully subscribed $eventName"
      )

    } catch {
      case error: ErrorException =>
        SocketIOSubscription.Ack(
          message = error.getMessage(),
          error = error.error.code
        )
    }

    if (ackSender.isAckRequested)
      ackSender.sendAckData(ackData)
  }
}
