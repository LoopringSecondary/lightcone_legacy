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
import io.lightcone.core.{MarketPair, MetadataManager}
import io.lightcone.lib.Address
import io.lightcone.relayer.data.SocketIOSubscription
import io.lightcone.relayer.data.cmc.ExternalMarketTickerInfo
import io.lightcone.relayer.socketio._

class TickerNotifier @Inject()(implicit manager: MetadataManager)
    extends SocketIONotifier[SocketIOSubscription.ParamsForTickers] {
  val eventName = "tickers"

  def isSubscriptionValid(
      subscription: SocketIOSubscription.ParamsForTickers
    ): Boolean = subscription.market.isDefined

  def wrapClient(
      client: SocketIOClient,
      subscription: SocketIOSubscription.ParamsForTickers
    ) =
    new SocketIOSubscriber(
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

  def shouldNotifyClient(
      subscription: SocketIOSubscription.ParamsForTickers,
      event: AnyRef
    ): Boolean = {
    event match {
      case ticker: ExternalMarketTickerInfo =>
        //TODO(yd)  确定使用地址还是symbol
        val baseTokenAddress =
          manager.getTokenWithSymbol(ticker.baseTokenSymbol).get.meta.address
        val quoteTokenAddress =
          manager.getTokenWithSymbol(ticker.quoteTokenSymbol).get.meta.address
        MarketPair(baseTokenAddress, quoteTokenAddress) == subscription.getMarket
      case _ => false

    }
  }

}
