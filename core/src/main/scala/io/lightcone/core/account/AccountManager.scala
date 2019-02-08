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

package io.lightcone.core

object AccountManager {

  def default(
    )(
      implicit
      dustEvaluator: DustOrderEvaluator,
      orderPool: AccountOrderPool with UpdatedOrdersTracing
    ): AccountManager = new AccountManagerImpl()
}

trait AccountManager {
  def hasReserveManager(token: String): Boolean
  def addReserveManager(tm: ReserveManager): ReserveManager
  def getReserveManager(token: String): ReserveManager
  def getOrUpdateReserveManager(tm: ReserveManager): ReserveManager

  def submitOrder(order: Matchable): Boolean

  // soft cancel an order
  def cancelOrder(orderId: String): Boolean

  // hard cancel multiple orders
  def handleCutoff(cutoff: Long): Int

  def purgeOrders(marketPair: MarketPair): Int

  def handleCutoff(
      cutoff: Long,
      marketHash: String
    ): Int

  def adjustOrder(
      orderId: String,
      outstandingAmountS: BigInt
    ): Boolean

  //TODO: 需要实现cancelOrdersInMarket与cancelAllOrders
  //由用户由前端请求，按照address和市场取消订单
  def cancelOrdersInMarket(marketHash: String): Int

  def cancelAllOrders(): Int
}
