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
import io.lightcone.core.Orderbook
import io.lightcone.lib.Address
import io.lightcone.relayer.data.SocketIOSubscription
import io.lightcone.relayer.socketio._

class OrderBookNotifier @Inject()
    extends SocketIONotifier[SocketIOSubscription.ParamsForOrderbook] {

  val name: String = "order_book"

  def isSubscriptionValid(subscription: SocketIOSubscription): Boolean =
    subscription.paramsForOrderbook.isDefined && subscription.getParamsForOrderbook.market.isDefined

  def wrapClient(
      client: SocketIOClient,
      subscription: SocketIOSubscription
    ) =
    subscription.paramsForOrderbook.map { params =>
      new SocketIOSubscriber(
        client,
        params.copy(
          market = params.market.map(
            market =>
              market.copy(
                baseToken = Address.normalize(market.baseToken),
                quoteToken = Address.normalize(market.quoteToken)
              )
          )
        )
      )
    }.get

  def shouldNotifyClient(
      subscription: SocketIOSubscription.ParamsForOrderbook,
      event: AnyRef
    ): Boolean = {
    event match {
      case orderBook: Orderbook.Update =>
        // TODO（yd）&& subscription.level == orderBook.level Update中应该有多个level的数据？
        subscription.market == orderBook.marketPair
      case _ => false
    }

  }

}
