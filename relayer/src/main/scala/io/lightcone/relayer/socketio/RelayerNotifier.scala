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

import io.lightcone.core.{
  MarketHash,
  MarketMetadata,
  MarketPair,
  Orderbook,
  RawOrder,
  TokenMetadata
}
import io.lightcone.lib.Address
import io.lightcone.persistence.{Activity, Fill}
import io.lightcone.relayer.data._

class RelayerNotifier() extends SocketIONotifier {
  import SocketIOSubscription._

  def isSubscriptionValid(subscription: SocketIOSubscription): Boolean =
    (subscription.paramsForAccounts.isEmpty ||
      (subscription.getParamsForAccounts.addresses.nonEmpty && subscription.getParamsForAccounts.addresses
        .forall(Address.isValid))) &&
      (subscription.paramsForActivities.isEmpty ||
        (subscription.getParamsForActivities.addresses.nonEmpty && subscription.getParamsForActivities.addresses
          .forall(Address.isValid))) &&
      (subscription.paramsForFills.isEmpty ||
        (subscription.getParamsForFills.market.isDefined && subscription.getParamsForFills.getMarket
          .isValid())) &&
      (subscription.paramsForOrderbook.isEmpty ||
        (subscription.getParamsForOrderbook.market.isDefined && subscription.getParamsForOrderbook.getMarket
          .isValid())) &&
      (subscription.paramsForOrders.isEmpty ||
        (subscription.getParamsForOrders.addresses.nonEmpty && subscription.getParamsForOrders.addresses
          .forall(Address.isValid))) &&
      (subscription.paramsForTickers.isEmpty ||
        (subscription.getParamsForTickers.market.isDefined && subscription.getParamsForTickers.getMarket
          .isValid()))

  def generateNotification(
      evt: AnyRef,
      subscription: SocketIOSubscription
    ): Option[Notification] = evt match {
    case e: AccountUpdate =>
      if (subscription.paramsForAccounts.isDefined &&
          subscription.getParamsForAccounts.addresses.contains(e.address) &&
          (subscription.getParamsForAccounts.tokens.isEmpty || e.tokenBalance.isEmpty ||
          subscription.getParamsForAccounts.tokens
            .contains(e.getTokenBalance.token)))
        Some(Notification(account = Some(e)))
      else None
    case e: Activity =>
      if (subscription.paramsForActivities.isDefined &&
          subscription.getParamsForActivities.addresses.contains(e.owner))
        Some(Notification(activity = Some(e)))
      else None
    case e: Fill =>
      if (subscription.paramsForFills.isDefined &&
          (subscription.getParamsForFills.address.isEmpty || subscription.getParamsForFills.address == e.owner)
          && (MarketHash(subscription.getParamsForFills.getMarket) ==
            MarketHash(MarketPair(e.tokenB, e.tokenS))))
        Some(Notification(fill = Some(e)))
      else None
    case e: Orderbook.Update =>
      if (subscription.paramsForOrderbook.isDefined && subscription.getParamsForOrderbook.market == e.marketPair)
        Some(Notification(orderbook = Some(e)))
      else None
    case order: RawOrder =>
      if (subscription.paramsForOrders.isDefined && subscription.getParamsForOrders.addresses
            .contains(order.owner) && (subscription.getParamsForOrders.market.isEmpty || MarketHash(
            subscription.getParamsForOrders.getMarket
          ) == MarketHash(MarketPair(order.tokenB, order.tokenS))))
        Some(Notification(order = Some(order)))
      else None
    case e: TokenMetadata =>
      if (subscription.paramsForTokens.isDefined)
        Some(Notification(tokenMetadata = Some(e)))
      else None
    case e: MarketMetadata =>
      if (subscription.paramsForMarkets.isDefined)
        Some(Notification(marketMetadata = Some(e)))
      else None

    case ticker: AnyRef => None // TODO(yadong) 等待永丰的PR合并
    case _              => None
  }

}
