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

import io.lightcone.core.OrderStatus._

import scala.concurrent._

object AccountManager {

  def default(
      owner: String,
      enableTracing: Boolean = false
    )(
      implicit
      updatedAccountsProcessor: UpdatedAccountsProcessor,
      updatedOrdersProcessor: UpdatedOrdersProcessor,
      provider: BalanceAndAllowanceProvider,
      ec: ExecutionContext
    ): AccountManager = new AccountManagerImpl(owner)
}

trait AccountManager {
  val owner: String

  def getNumOfOrders(): Int

  def getAccountInfo(token: String): Future[AccountInfo]

  def getAccountInfo(tokens_ : Set[String]): Future[Map[String, AccountInfo]]

  def setBalanceAndAllowance(
      blockNumber: Long,
      token: String,
      balance: BigInt,
      allowance: BigInt
    ): Future[Map[String, Matchable]]

  def setBalance(
      blockNumber: Long,
      token: String,
      balance: BigInt
    ): Future[Map[String, Matchable]]

  def setAllowance(
      blockNumber: Long,
      token: String,
      allowance: BigInt
    ): Future[Map[String, Matchable]]

  def resubmitOrder(order: Matchable): Future[(Boolean, Map[String, Matchable])]

  // soft cancel an order
  def cancelOrder(
      orderId: String,
      status: OrderStatus = STATUS_SOFT_CANCELLED_BY_USER
    ): Future[(Boolean, Map[String, Matchable])]

  def cancelOrders(orderIds: Seq[String]): Future[Map[String, Matchable]]
  def cancelOrders(marketPair: MarketPair): Future[Map[String, Matchable]]
  def cancelAllOrders(): Future[Map[String, Matchable]]

  // cancel an order based on onchain cancel event
  def hardCancelOrder(
      blockNumber: Long,
      orderId: String
    ): Future[Map[String, Matchable]]

  // hard cancel multiple orders
  def handleCutoff(
      blockNumber: Long,
      cutoff: Long
    ): Future[Map[String, Matchable]]

  def handleCutoff(
      blockNumber: Long,
      cutoff: Long,
      marketHash: String
    ): Future[Map[String, Matchable]]

  def purgeOrders(marketPair: MarketPair): Future[Map[String, Matchable]]
}
