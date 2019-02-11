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

trait UpdatedOrdersProcessor {
  implicit val ec: ExecutionContext

  def processOrder(order: Matchable): Future[Any]

  def processOrders(orders: Map[String, Matchable]): Future[Any] = {
    val futures = orders.values.map(processOrder)
    Future.sequence(futures)
  }
}

trait ReserveEventHandler {

  def onTokenReservedForOrder(
      orderId: String,
      token: String,
      amount: BigInt
    ): Unit
}

object ReserveManagerAlt {

  def default(
      token: String
    )(
      implicit
      eventHandler: ReserveEventHandler
    ): ReserveManagerAlt =
    new ReserveManagerAltClassicImpl(token)
}

private[core] trait ReserveManagerAlt {
  val token: String

  def getAccountInfo(): AccountInfo

  def setBalance(balance: BigInt): Set[String]
  def setAllowance(allowance: BigInt): Set[String]

  def setBalanceAndAllowance(
      balance: BigInt,
      allowance: BigInt
    ): Set[String]

  // Reserve or adjust the reserve of the balance/allowance for an order, returns the order ids to cancel.
  def reserve(
      orderId: String,
      requestedAmount: BigInt
    ): Set[String]

  // Release balance/allowance for an order, returns the order ids to cancel.
  def release(orderIds: Seq[String]): Set[String]
  def release(orderId: String): Set[String] = release(Seq(orderId))

  def clearOrders(): Unit
}
