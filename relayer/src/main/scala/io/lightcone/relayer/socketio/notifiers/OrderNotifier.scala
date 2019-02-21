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
import io.lightcone.core._
import io.lightcone.lib.{Address, NumericConversion}
import io.lightcone.relayer.socketio._

class OrderNotifier @Inject()
    extends SocketIONotifier[SocketIOSubscription.ParamsForOrderUpdate] {

  val eventName = "orders"

  def wrapClient(
      client: SocketIOClient,
      subscription: SocketIOSubscription.ParamsForOrderUpdate
    ): SocketIOSubscriber[SocketIOSubscription.ParamsForOrderUpdate] =
    new SocketIOSubscriber[SocketIOSubscription.ParamsForOrderUpdate](
      client,
      subscription.copy(
        addresses = subscription.addresses.map(Address.normalize),
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
      subscription: SocketIOSubscription.ParamsForOrderUpdate,
      event: AnyRef
    ): Option[AnyRef] = {
    event match {
      case order: OrderUpdate =>
        if (subscription.addresses.contains(order.owner) && subscription.market == order.marketPair) {
          Some(
            Order(
              hash = order.orderId,
              status = order.status.name,
              dealtAmountB = order.state
                .map(state => NumericConversion.toHexString(state.amountB))
                .getOrElse(""),
              dealtAmountS = order.state
                .map(state => NumericConversion.toHexString(state.amountB))
                .getOrElse(""),
              dealtAmountFee = order.state
                .map(state => NumericConversion.toHexString(state.amountFee))
                .getOrElse("")
            )
          )
        } else None
      case _ => None
    }
  }
}
