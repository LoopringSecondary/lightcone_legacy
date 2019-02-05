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

package org.loopring.lightcone.core

/// import org.loopring.lightcone.proto._

class OrderAwareOrderbookAggregatorImpl(
    priceDecimals: Int,
    precisionForAmount: Int,
    precisionForTotal: Int
  )(
    implicit
    marketPair: MarketPair,
    metadataManager: MetadataManager)
    extends OrderbookAggregatorImpl(
      priceDecimals,
      precisionForAmount,
      precisionForTotal
    )
    with OrderAwareOrderbookAggregator {

  def addOrder(order: Matchable) =
    adjustAmount(
      order.isSell,
      true,
      order.price,
      order.matchableBaseAmount,
      order.matchableQuoteAmount
    )

  def deleteOrder(order: Matchable) =
    adjustAmount(
      order.isSell,
      false,
      order.price,
      order.matchableBaseAmount,
      order.matchableQuoteAmount
    )
}
