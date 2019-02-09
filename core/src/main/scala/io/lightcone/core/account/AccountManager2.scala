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

import scala.concurrent.Future

object AccountManager2 {

  // def default(
  //   )(
  //     implicit
  //     dustEvaluator: DustOrderEvaluator,
  //     orderPool: AccountOrderPool with UpdatedOrdersTracing
  //   ): AccountManager2 = new AccountManagerImpl2()
}

trait AccountManager2 {
  val address: String

  def setBalanceAndAllowance(
      token: String,
      balance: BigInt,
      allowance: BigInt
    ): Future[Int]

  def resubmitOrder(order: Matchable): Future[Boolean]

  // cancel an order based on onchain cancel event
  def hardCancelOrder(orderId: String): Future[Boolean]
  // soft cancel an order
  def cancelOrder(orderId: String): Future[Boolean]
  def cancelOrders(orderIds: Seq[String]): Future[Int]
  def cancelOrders(marketPair: MarketPair): Future[Int]
  def cancelAllOrders(): Future[Int]

  // hard cancel multiple orders
  def handleCutoff(cutoff: Long): Future[Int]

  def handleCutoff(
      cutoff: Long,
      marketHash: String
    ): Future[Int]

  def purgeOrders(marketPair: MarketPair): Future[Int]
}
