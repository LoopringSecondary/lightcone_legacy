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

import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data._

import org.slf4s.Logging
import XOrderStatus._

/*
 * AccountTokenManagerImpl manages reserving balance and allowance for orders.
 * An order can be 'reserved' if and only if the available (unservered) balance
 * is no less than the order's size.
 */

trait AccountTokenManager {
  val token: String
  val maxNumOrders: Int

  def getBalance(): BigInt
  def getAllowance(): BigInt
  def getAvailableBalance(): BigInt
  def getAvailableAllowance(): BigInt

  def hasTooManyOrders(): Boolean

  def setBalance(balance: BigInt): Set[String]
  def setAllowance(allowance: BigInt): Set[String]
  def setBalanceAndAllowance(balance: BigInt, allowance: BigInt): Set[String]

  // Reserve balance/allowance for an order.
  def reserve(orderId: String): Set[String]

  // Release balance/allowance for an order.
  def release(orderId: String): Set[String]

  // Rebalance due to change of an order.
  def adjust(orderId: String): Set[String]
}
