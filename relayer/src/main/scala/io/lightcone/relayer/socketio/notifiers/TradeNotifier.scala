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
import io.lightcone.relayer.socketio._

class TradeNotifier @Inject() extends SocketIONotifier[SubscribeTrade] {

  val eventName: String = "trades"

  def wrapClient(
      client: SocketIOClient,
      subscription: SubscribeTrade
    ): SocketIOSubscriber[SubscribeTrade] =
    new SocketIOSubscriber[SubscribeTrade](
      client,
      subscription = subscription.copy(
        addresses = subscription.addresses.map(Address.normalize),
        market = subscription.market.copy(
          baseToken = Address.normalize(subscription.market.baseToken),
          quoteToken = Address.normalize(subscription.market.quoteToken)
        )
      )
    )

  def shouldNotifyClient(
      subscription: SubscribeTrade,
      event: AnyRef
    ): Boolean = {

    event match {
      case trade: Trade =>
        subscription.addresses.contains(trade.owner) && (MarketHash(
          MarketPair(
            subscription.market.baseToken,
            subscription.market.quoteToken
          )
        ) == MarketHash(MarketPair(trade.tokenB, trade.tokenS)))

      case _ => false
    }

  }

}
