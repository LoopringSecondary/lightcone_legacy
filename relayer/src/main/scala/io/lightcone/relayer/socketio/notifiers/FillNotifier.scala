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

package io.lightcone.relayer.socketio.notifiers

import com.corundumstudio.socketio.SocketIOClient
import com.google.inject.Inject
import io.lightcone.core.{MarketHash, MarketPair}
import io.lightcone.lib.Address
import io.lightcone.persistence.Fill
import io.lightcone.relayer.data.SocketIOSubscription
import io.lightcone.relayer.socketio._

class FillNotifier @Inject()
    extends SocketIONotifier[SocketIOSubscription.ParamsForFills] {

  val eventName: String = "trades"

  def isSubscriptionValid(
      subscription: SocketIOSubscription.ParamsForFills
    ): Boolean =
    subscription.market.isDefined && (subscription.address.isEmpty || Address
      .isValid(subscription.address))

  def wrapClient(
      client: SocketIOClient,
      subscription: SocketIOSubscription.ParamsForFills
    ) = {
    val address = if (subscription.address.nonEmpty) {
      Address.normalize(subscription.address)
    } else {
      subscription.address
    }
    new SocketIOSubscriber(
      client,
      subscription = subscription.copy(
        address = address,
        market = subscription.market.map(
          market =>
            market.copy(
              baseToken = Address.normalize(market.baseToken),
              quoteToken = Address.normalize(market.quoteToken)
            )
        )
      )
    )
  }

  def shouldNotifyClient(
      subscription: SocketIOSubscription.ParamsForFills,
      event: AnyRef
    ): Boolean = event match {
    case fill: Fill =>
      (subscription.address.isEmpty || subscription.address == fill.owner) && (MarketHash(
        subscription.getMarket
      ) == MarketHash(MarketPair(fill.tokenB, fill.tokenS)))
    case _ => false
  }
}
