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

  def checkSubscriptionValidation(subscription: SocketIOSubscription): Unit = {

    if (subscription.paramsForActivities.isDefined &&
        (subscription.getParamsForActivities.addresses.isEmpty ||
        !subscription.getParamsForActivities.addresses.forall(Address.isValid)))
      throw ErrorException(
        code = ErrorCode.ERR_INVALID_SOCKETIO_SUBSCRIPTION,
        message = s"invalid ParamsForActivities:${subscription.getParamsForActivities}, " +
          s"addresses shouldn't be empty and must be valid ethereum addresses"
      )
    else if (subscription.paramsForOrders.isDefined &&
             (subscription.getParamsForOrders.addresses.isEmpty ||
             !subscription.getParamsForOrders.addresses.forall(
               Address.isValid
             )))
      throw ErrorException(
        code = ErrorCode.ERR_INVALID_SOCKETIO_SUBSCRIPTION,
        s"invalid ParamsForOrders:${subscription.getParamsForOrders}, " +
          s"addresses shouldn't be empty and must be valid ethereum addresses"
      )
    else if (subscription.paramsForFills.isDefined &&
             (subscription.getParamsForFills.marketPair.isEmpty ||
             !subscription.getParamsForFills.getMarketPair.isValid()))
      throw ErrorException(
        code = ErrorCode.ERR_INVALID_SOCKETIO_SUBSCRIPTION,
        s"invalid ParamsForFills:${subscription.getParamsForFills}," +
          s" market shouldn't be null and market token addresses must be valid ethereum addresses"
      )
    else if (subscription.paramsForOrderbook.isDefined &&
             (subscription.getParamsForOrderbook.marketPair.isEmpty ||
             !subscription.getParamsForOrderbook.getMarketPair.isValid()))
      throw ErrorException(
        code = ErrorCode.ERR_INVALID_SOCKETIO_SUBSCRIPTION,
        s"invalid ParamsForOrderbook:${subscription.getParamsForOrderbook}, " +
          s"market shouldn't be null and market token addresses must be valid ethereum addresses"
      )
    else if (subscription.paramsForInternalTickers.isDefined &&
             (subscription.getParamsForInternalTickers.marketPair.isEmpty ||
             !subscription.getParamsForInternalTickers.getMarketPair.isValid()))
      throw ErrorException(
        code = ErrorCode.ERR_INVALID_SOCKETIO_SUBSCRIPTION,
        s"invalid paramsForInternalTickers:${subscription.getParamsForInternalTickers}," +
          s" market shouldn't be null and market token addresses must be valid ethereum addresses"
      )
    else if (subscription.paramsForAccounts.isDefined &&
             (subscription.getParamsForAccounts.addresses.isEmpty ||
             !subscription.getParamsForAccounts.addresses.forall(
               Address.isValid
             )))
      throw ErrorException(
        code = ErrorCode.ERR_INVALID_SOCKETIO_SUBSCRIPTION,
        s"invalid ParamsForAccounts:${subscription.getParamsForAccounts}, " +
          s"addresses shouldn't be empty and must be valid ethereum addresses"
      )
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
            if (params.getMarketPair.hashString ==
              MarketPair(e.tokenB, e.tokenS).hashString) &&
              ((params.address.isEmpty && e.isTaker) || params.address == e.owner) =>
          Some(Notification(fill = Some(e)))
        case _ => None
      }

    case e: Orderbook.Update =>
      subscription.paramsForOrderbook match {
        case Some(params)
            if params.marketPair.map(_.hashString) == e.marketPair
              .map(_.hashString) && params.level == e.level =>
          Some(Notification(orderbook = Some(e)))
        case _ => None
      }

    case order: RawOrder =>
      subscription.paramsForOrders match {
        case Some(params)
            if params.addresses
              .contains(order.owner) && (params.marketPair.isEmpty
              || params.getMarketPair.hashString ==
                MarketPair(order.tokenB, order.tokenS).hashString) =>
          Some(Notification(order = Some(order)))
        case _ => None
      }

    case e: MetadataChanged =>
      subscription.paramsForMetadata match {
        case Some(_) => Some(Notification(metadataChanged = Some(e)))
        case _       => None
      }

    case ticker: OHLCRawData =>
      subscription.paramsForInternalTickers match {
        case Some(params) =>
          if (ticker.marketHash == MarketHash(params.getMarketPair)
                .hashString()) {
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
