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

import io.lightcone.core._
import io.lightcone.lib.Address
import io.lightcone.persistence._
import io.lightcone.relayer.data._

class RelayerNotifier() extends SocketIONotifier {
  import SocketIOSubscription._

  def checkSubscriptionValidation(
      subscription: SocketIOSubscription
    ): Option[String] = {
    subscription match {
      case SocketIOSubscription(Some(params), _, _, _, _, _, _, _, _)
          if params.addresses.isEmpty ||
            !params.addresses.forall(Address.isValid) =>
        Some(
          s"invalid ParamsForActivities:$params, " +
            s"addresses shouldn't be empty and must be valid ethereum addresses"
        )

      case SocketIOSubscription(_, Some(paramsForOrders), _, _, _, _, _, _, _)
          if paramsForOrders.addresses.isEmpty ||
            !paramsForOrders.addresses.forall(Address.isValid) =>
        Some(
          s"invalid ParamsForOrders:$paramsForOrders, " +
            s"addresses shouldn't be empty and must be valid ethereum addresses"
        )

      case SocketIOSubscription(_, _, Some(paramsForFills), _, _, _, _, _, _)
          if paramsForFills.market.isEmpty ||
            !paramsForFills.getMarket.isValid() =>
        Some(
          s"invalid ParamsForFills:$paramsForFills," +
            s" market shouldn't be null and market token addresses must be valid ethereum addresses"
        )

      case SocketIOSubscription(_, _, _, Some(params), _, _, _, _, _)
          if params.market.isEmpty || !params.getMarket.isValid() =>
        Some(
          s"invalid ParamsForOrderbook:$params, " +
            s"market shouldn't be null and market token addresses must be valid ethereum addresses"
        )

      case SocketIOSubscription(_, _, _, _, _, _, Some(paramsForTickers), _, _)
          if paramsForTickers.market.isEmpty ||
            !paramsForTickers.getMarket.isValid() =>
        Some(
          s"invalid paramsForTickers:$paramsForTickers," +
            s" market shouldn't be null and market token addresses must be valid ethereum addresses"
        )

      case SocketIOSubscription(_, _, _, _, _, _, _, _, Some(paramsForAccounts))
          if paramsForAccounts.addresses.isEmpty ||
            !paramsForAccounts.addresses.forall(Address.isValid) =>
        Some(
          s"invalid ParamsForAccounts:$paramsForAccounts, " +
            s"addresses shouldn't be empty and must be valid ethereum addresses"
        )

      case _ => None
    }

  }

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
          (subscription.getParamsForFills.address.isEmpty ||
          subscription.getParamsForFills.address == e.owner)
          && (MarketHash(subscription.getParamsForFills.getMarket) ==
            MarketHash(MarketPair(e.tokenB, e.tokenS))))
        Some(Notification(fill = Some(e)))
      else None

    case e: Orderbook.Update =>
      if (subscription.paramsForOrderbook.isDefined &&
          subscription.getParamsForOrderbook.market == e.marketPair)
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
