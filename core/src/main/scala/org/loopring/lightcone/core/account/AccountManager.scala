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
import org.loopring.lightcone.lib.TimeProvider

trait AccountManager {
  def hasTokenManager(token: String): Boolean
  def addTokenManager(tm: AccountTokenManager): AccountTokenManager
  def getTokenManager(token: String): AccountTokenManager

  def getOrUpdateTokenManager(
      token: String,
      tm: AccountTokenManager
    ): AccountTokenManager

  def submitOrder(order: Matchable): Boolean

  def submitAndGetUpdatedOrders(
      order: Matchable
    ): (Boolean, Map[String, Matchable])

  def cancelOrder(orderId: String): Boolean

  def adjustOrder(
      orderId: String,
      outstandingAmountS: BigInt
    ): Boolean

}

object AccountManager {

  def default(
    )(
      implicit orderPool: AccountOrderPool with UpdatedOrdersTracing,
      timeProvider: TimeProvider
    ): AccountManager = new AccountManagerImpl()
}
