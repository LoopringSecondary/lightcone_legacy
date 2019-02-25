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

  def getBalanceOfToken(token: String): Future[BalanceOfToken]

  def getBalanceOfToken(
      tokens_ : Set[String]
    ): Future[Map[String, BalanceOfToken]]

  def setBalanceAndAllowance(
      block: Long,
      token: String,
      balance: BigInt,
      allowance: BigInt
    ): Future[Map[String, Matchable]]

  def setBalance(
      block: Long,
      token: String,
      balance: BigInt
    ): Future[Map[String, Matchable]]

  def setAllowance(
      block: Long,
      token: String,
      allowance: BigInt
    ): Future[Map[String, Matchable]]

  def resubmitOrder(order: Matchable): Future[(Boolean, Map[String, Matchable])]

  // mark an order is cancelled on chain
  def hardCancelOrder(
      block: Long,
      orderId: String
    ): Future[Map[String, Matchable]]

  // soft cancel an order
  def cancelOrder(
      orderId: String,
      status: OrderStatus = OrderStatus.STATUS_SOFT_CANCELLED_BY_USER
    ): Future[(Boolean, Map[String, Matchable])]

  def cancelOrders(orderIds: Seq[String]): Future[Map[String, Matchable]]
  def cancelOrders(marketPair: MarketPair): Future[Map[String, Matchable]]
  def cancelAllOrders(): Future[Map[String, Matchable]]

  def purgeOrders(marketPair: MarketPair): Future[Map[String, Matchable]]

  def doesOrderSatisfyCutoff(
      orderValidSince: Long,
      marketHash: String
    ): Boolean

  def setCutoff(
      block: Long,
      cutoff: Long,
      marketHash: Option[String]
    ): Future[Map[String, Matchable]]

}
