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

trait ReserveEventHandler {

  def onTokenReservedForOrder(
    block: Long,
    orderId: String,
    token: String,
    amount: BigInt): Unit
}

object ReserveManager {

  def default(
    token: String,
    enableTracing: Boolean = false)(
      implicit eventHandler: ReserveEventHandler): ReserveManager =
    new ReserveManagerImpl(token, enableTracing)
}

private[core] trait ReserveManager {
  val token: String

  def getBlock(): Long // return the last block number

  def getBalanceOfToken(): BalanceOfToken

  def setBalance(
    block: Long,
    balance: BigInt): Set[String]

  def setAllowance(
    block: Long,
    allowance: BigInt): Set[String]

  def setBalanceAndAllowance(
    block: Long,
    balance: BigInt,
    allowance: BigInt): Set[String]

  // Reserve or adjust the reserve of the balance/allowance for an order, returns the order ids to cancel.
  def reserve(
    orderId: String,
    requestedAmount: BigInt): Set[String]

  // Release balance/allowance for an order, returns the order ids to cancel.
  def release(orderIds: Set[String]): Set[String]
  def release(orderId: String): Set[String] = release(Set(orderId))

  def clearOrders(): Unit
}
