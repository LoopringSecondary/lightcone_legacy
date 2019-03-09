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

import com.google.inject.Inject
import io.lightcone.core._
import io.lightcone.lib.Address
import io.lightcone.ethereum.persistence._
import io.lightcone.relayer.data._

class RelayerNotifier @Inject()(implicit val metadataManager: MetadataManager)
    extends SocketIONotifier {
  import SocketIOSubscription._

  def checkSubscriptionValidation(
      subscription: SocketIOSubscription
    ): Option[String] = {
    subscription match {
      case SocketIOSubscription(Some(params), _, _, _, _, _, _, _, _, _)
          if params.addresses.isEmpty ||
            !params.addresses.forall(Address.isValid) =>
        Some(
          s"invalid ParamsForActivities:$params, " +
            s"addresses shouldn't be empty and must be valid ethereum addresses"
        )

      case SocketIOSubscription(_, Some(params), _, _, _, _, _, _, _, _)
          if params.addresses.isEmpty ||
            !params.addresses.forall(Address.isValid) =>
        Some(
          s"invalid ParamsForOrders:$params, " +
            s"addresses shouldn't be empty and must be valid ethereum addresses"
        )

      case SocketIOSubscription(_, _, Some(paramsForFills), _, _, _, _, _, _, _)
          if paramsForFills.market.isEmpty ||
            !paramsForFills.getMarket.isValid() =>
        Some(
          s"invalid ParamsForFills:$paramsForFills," +
            s" market shouldn't be null and market token addresses must be valid ethereum addresses"
        )

      case SocketIOSubscription(_, _, _, Some(params), _, _, _, _, _, _)
          if params.market.isEmpty || !params.getMarket.isValid() =>
        Some(
          s"invalid ParamsForOrderbook:$params, " +
            s"market shouldn't be null and market token addresses must be valid ethereum addresses"
        )

      case SocketIOSubscription(_, _, _, _, _, _, Some(params), _, _, _)
          if params.market.isEmpty || !params.getMarket.isValid() =>
        Some(
          s"invalid paramsForTickers:$params," +
            s" market shouldn't be null and market token addresses must be valid ethereum addresses"
        )

      case SocketIOSubscription(_, _, _, _, _, _, _, Some(params), _, _)
          if params.market.isEmpty || !params.getMarket.isValid() =>
        Some(
          s"invalid paramsForInternalTickers:$params," +
            s" market shouldn't be null and market token addresses must be valid ethereum addresses"
        )

      case SocketIOSubscription(_, _, _, _, _, _, _, _, _, Some(params))
          if params.addresses.isEmpty ||
            !params.addresses.forall(Address.isValid) =>
        Some(
          s"invalid ParamsForAccounts:$params, " +
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
      subscription.paramsForAccounts match {
        case Some(params)
            if params.addresses.contains(e.address) &&
              (params.tokens.isEmpty || e.tokenBalance.isEmpty ||
                params.tokens.contains(e.getTokenBalance.token)) =>
          Some(Notification(account = Some(e)))
        case _ => None
      }

    case e: Activity =>
      subscription.paramsForActivities match {
        case Some(params) if params.addresses.contains(e.owner) =>
          Some(Notification(activity = Some(e)))
        case _ => None
      }

    case e: Fill =>
      subscription.paramsForFills match {
        case Some(params)
            if (params.address.isEmpty || params.address == e.owner)
              && (params.getMarket.hashString ==
                MarketPair(e.tokenB, e.tokenS).hashString) =>
          Some(Notification(fill = Some(e)))
        case _ => None
      }

    case e: Orderbook.Update =>
      subscription.paramsForOrderbook match {
        case Some(params)
            if params.market.map(_.hashString) == e.marketPair
              .map(_.hashString) =>
          Some(Notification(orderbook = Some(e)))
        case _ => None
      }

    case order: RawOrder =>
      subscription.paramsForOrders match {
        case Some(params)
            if params.addresses.contains(order.owner) && (params.market.isEmpty
              || params.getMarket.hashString ==
                MarketPair(order.tokenB, order.tokenS).hashString) =>
          Some(Notification(order = Some(order)))
        case _ => None
      }

    case e: TokenMetadata =>
      subscription.paramsForTokens match {
        case Some(_) => Some(Notification(tokenMetadata = Some(e)))
        case _       => None
      }

    case e: MarketMetadata =>
      subscription.paramsForMarkets match {
        case Some(_) =>
          Some(Notification(marketMetadata = Some(e)))
        case _ => None
      }
    case ticker: MarketTicker =>
      subscription.paramsForTickers match {
        case Some(params) =>
          if (MarketPair(ticker.baseToken, ticker.quoteToken).hashString
                == params.getMarket.hashString)
            Some(Notification(ticker = Some(ticker)))
          else None

        case _ => None
      }
    case ticker: OHLCRawData =>
      subscription.paramsForInternalTickers match {
        case Some(params) =>
          if (ticker.marketHash == MarketHash(params.getMarket).hashString()) {
            try {
              val market = metadataManager.getMarket(ticker.marketHash)
              Some(
                Notification(
                  internalTicker = Some(
                    MarketTicker(
                      baseToken = market.getMetadata.getMarketPair.baseToken,
                      quoteToken = market.getMetadata.getMarketPair.quoteToken,
                      price = ticker.price
                    )
                  )
                )
              )
            } catch {
              case _: Throwable =>
                None
            }
          } else None

        case _ => None
      }
    case _ => None
  }
}
