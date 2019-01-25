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

package org.loopring.lightcone.core.account
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.proto.{MarketId, OrderStatus}

object AccountManager {

  def default(
    )(
      implicit
      dustEvaluator: DustOrderEvaluator,
      orderPool: AccountOrderPool with UpdatedOrdersTracing
    ): AccountManager = new AccountManagerImpl()
}

trait AccountManager {
  def hasTokenManager(token: String): Boolean
  def addTokenManager(tm: AccountTokenManager): AccountTokenManager
  def getTokenManager(token: String): AccountTokenManager
  def getOrUpdateTokenManager(tm: AccountTokenManager): AccountTokenManager

  def submitOrder(order: Matchable): Boolean

  // soft cancel an order
  def cancelOrder(orderId: String): Boolean

  // hard cancel multiple orders
  def handleCutoff(cutoff: Long): Int

  def purgeOrders(marketId: MarketId): Int

  def handleCutoff(
      cutoff: Long,
      marketKey: String
    ): Int

  def adjustOrder(
      orderId: String,
      outstandingAmountS: BigInt
    ): Boolean

}
