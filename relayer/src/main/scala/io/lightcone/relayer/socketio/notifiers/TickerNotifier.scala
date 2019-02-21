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
import io.lightcone.lib.Address
import io.lightcone.relayer.socketio._

class TickerNotifier @Inject()
    extends SocketIONotifier[SocketIOSubscription.ParamsForTicker] {
  val eventName = "tickers"

  def wrapClient(
      client: SocketIOClient,
      subscription: SocketIOSubscription.ParamsForTicker
    ): SocketIOSubscriber[SocketIOSubscription.ParamsForTicker] =
    new SocketIOSubscriber[SocketIOSubscription.ParamsForTicker](
      client,
      subscription.copy(
        market = subscription.market.map(
          market =>
            market.copy(
              baseToken = Address.normalize(market.baseToken),
              quoteToken = Address.normalize(market.quoteToken)
            )
        )
      )
    )

  def extractNotifyData(
      subscription: SocketIOSubscription.ParamsForTicker,
      event: AnyRef
    ): Option[AnyRef] = {
    event match {
      case ticker: Ticker =>
        Some(ticker) // TODO 等待ticker实现
      case _ => None

    }
  }

}
