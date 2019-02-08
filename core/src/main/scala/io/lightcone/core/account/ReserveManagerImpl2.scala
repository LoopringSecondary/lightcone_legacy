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

import org.slf4s.Logging

// TODO(dongw): cancel all orders
// TODO(dongw): cancel all orders in a market
// TODO(dongw): optimize timing
// TODO(dongw): 时间借位？？？

case class Reserve(
    balance: BigInt,
    allowance: BigInt,
    availableBalance: BigInt,
    availableAllowance: BigInt,
    numOfOrders: Int)

trait ReserveManager2 {
  val token: String
  val maxNumOrders: Int

  def getReserve(): Reserve

  def hasTooManyOrders(): Boolean

  def setBalance(balance: BigInt): Unit
  def setAllowance(allowance: BigInt): Unit

  def setBalanceAndAllowance(
      balance: BigInt,
      allowance: BigInt
    ): Unit

  // Reserve balance/allowance for an order, returns the order ids to cancel.
  def reserve(orderId: String): Set[String]

  // Release balance/allowance for an order, returns the order ids to cancel.
  def release(orderId: String): Set[String]

  // Rebalance due to change of an order, returns the order ids to cancel.
  def adjust(orderId: String): Set[String]
}

abstract class ReserveManagerImpl2(
    val token: String,
    val maxNumOrders: Int = 1000
  )(
    implicit
    orderPool: AccountOrderPool,
    dustEvaluator: DustOrderEvaluator)
    extends ReserveManager2
    with Logging {}
