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

object AccountManager2 {

  def default(
      address: String
    )(
      implicit
      provider: BalanceAndAllowanceProvider,
      ec: ExecutionContext,
      orderPool: AccountOrderPool with UpdatedOrdersTracing
    ): AccountManager2 = new AccountManager2Impl(address)
}

trait AccountManager2 {
  val address: String

  def getAccountInfo(token: String): Future[AccountInfo]

  def setBalanceAndAllowance(
      token: String,
      balance: BigInt,
      allowance: BigInt
    ): Future[Map[String, Matchable]]

  def setBalance(
      token: String,
      balance: BigInt
    ): Future[Map[String, Matchable]]

  def setAllowance(
      token: String,
      allowance: BigInt
    ): Future[Map[String, Matchable]]

  def resubmitOrder(order: Matchable): Future[(Boolean, Map[String, Matchable])]

  // soft cancel an order
  def cancelOrder(orderId: String): Future[(Boolean, Map[String, Matchable])]
  def cancelOrders(orderIds: Seq[String]): Future[Map[String, Matchable]]
  def cancelOrders(marketPair: MarketPair): Future[Map[String, Matchable]]
  def cancelAllOrders(): Future[Map[String, Matchable]]

  // cancel an order based on onchain cancel event
  def hardCancelOrder(orderId: String): Future[Map[String, Matchable]]

  // hard cancel multiple orders
  def handleCutoff(cutoff: Long): Future[Map[String, Matchable]]

  def handleCutoff(
      cutoff: Long,
      marketHash: String
    ): Future[Map[String, Matchable]]

  def purgeOrders(marketPair: MarketPair): Future[Map[String, Matchable]]
}
