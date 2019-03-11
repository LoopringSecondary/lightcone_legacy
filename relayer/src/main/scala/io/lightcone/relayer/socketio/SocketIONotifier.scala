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
import io.lightcone.core.ErrorCode
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

  def checkSubscriptionValidation(
      subscription: SocketIOSubscription
    ): Option[String]

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

    log.debug(s"socketio notify: $event to ${targets.size} subscribers")
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
          market = params.market.map(_.normalize())
        )
      },
      paramsForOrderbook = subscription.paramsForOrderbook.map(
        params =>
          params.copy(
            market = params.market.map(_.normalize())
          )
      ),
      paramsForOrders = subscription.paramsForOrders.map(
        params =>
          params.copy(
            addresses = params.addresses.map(Address.normalize),
            market = params.market.map(_.normalize())
          )
      ),
      paramsForMarketTickers = subscription.paramsForMarketTickers.map(
        params =>
          params.copy(
            markets = params.markets.map(_.normalize())
          )
      ),
      paramsForTokenTickers = subscription.paramsForTokenTickers.map(
        params =>
          params.copy(
            tokens = params.tokens.map(Address.normalize)
          )
      ),
      paramsForInternalTickers = subscription.paramsForInternalTickers.map(
        params =>
          params.copy(
            market = params.market.map(_.normalize())
          )
      )
    )

  def onData(
      client: SocketIOClient,
      subscription: SocketIOSubscription,
      ackSender: AckRequest
    ) = {

    val error = checkSubscriptionValidation(subscription)

    if (ackSender.isAckRequested) {
      val ack =
        if (error.isEmpty) {
          SocketIOSubscription.Ack(
            message = s"$subscription successfully subscribed $eventName"
          )
        } else {
          SocketIOSubscription.Ack(
            message = error.get,
            error = ErrorCode.ERR_INVALID_SOCKETIO_SUBSCRIPTION
          )
        }
      ackSender.sendAckData(ack)
    }

    if (error.isEmpty) {
      val wrapped =
        new SocketIOSubscriber(client, normalizeSubscription(subscription))
      clients = wrapped +: clients.filterNot(_ == wrapped)
    }
  }
}
