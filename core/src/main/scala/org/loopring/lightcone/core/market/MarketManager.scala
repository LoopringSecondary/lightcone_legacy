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

package org.loopring.lightcone.core.market

import org.loopring.lightcone.core.data._

object MarketManager {
  case class MatchResult(
      rings: Seq[OrderRing],
      taker: Order,
      orderbookUpdate: XOrderbookUpdate
  )
}
trait MarketManager {
  import MarketManager._

  val marketId: XMarketId
  val pendingRingPool: PendingRingPool

  def submitOrder(order: Order, minFiatValue: Double): MatchResult
  def cancelOrder(orderId: String): Option[XOrderbookUpdate]
  def deletePendingRing(ringId: String): Option[XOrderbookUpdate]

  def getOrder(orderId: String, returnMatchableAmounts: Boolean = false): Option[Order]
  def getSellOrders(num: Int, returnMatchableAmounts: Boolean = false): Seq[Order]
  def getBuyOrders(num: Int, returnMatchableAmounts: Boolean = false): Seq[Order]

  def getNumOfOrders(): Int
  def getNumOfBuyOrders(): Int
  def getNumOfSellOrders(): Int

  def getMetadata(): MarketMetadata

  def triggerMatch(
    sellOrderAsTaker: Boolean,
    minFiatValue: Double,
    offset: Int = 0
  ): Option[MatchResult]
}
