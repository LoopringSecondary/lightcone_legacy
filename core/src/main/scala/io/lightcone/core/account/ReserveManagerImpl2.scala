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

case class ReserveStats(
    balance: BigInt,
    allowance: BigInt,
    availableBalance: BigInt,
    availableAllowance: BigInt,
    numOfOrders: Int,
    maxNumOrders: Int)

trait ReserveManager2 {
  val token: String
  val maxNumOrders: Int

  def getReserveStats(): ReserveStats

  def hasTooManyOrders(): Boolean

  def setBalance(balance: BigInt): Set[String]
  def setAllowance(allowance: BigInt): Set[String]

  def setBalanceAndAllowance(
      balance: BigInt,
      allowance: BigInt
    ): Set[String]

  // Reserve or adjust the reserve of the balance/allowance for an order, returns the order ids to cancel.
  def reserve(orderId: String): Set[String]

  // Release balance/allowance for an order, returns the order ids to cancel.
  def release(orderId: String): Set[String]
}

abstract class ReserveManagerImpl2(
    val token: String,
    val maxNumOrders: Int = 1000
  )(
    implicit
    orderPool: AccountOrderPool,
    dustEvaluator: DustOrderEvaluator)
    extends ReserveManager2
    with Logging {

  case class Reserve(
      orderId: String,
      reserved: BigInt)

  private var allowance: BigInt = _
  private var balance: BigInt = _
  private var spendable: BigInt = _
  private var reserved: BigInt = _

  private var reserves = List.empty[Reserve]
  private var firstWaiting = 0

  def getReserveStats() =
    ReserveStats(
      balance,
      allowance,
      balance - reserved,
      allowance - reserved,
      reserves.size,
      maxNumOrders
    )

  def hasTooManyOrders() = reserves.size > maxNumOrders

  def setBalance(balance: BigInt) =
    setBalanceAndAllowance(balance, this.allowance)

  def setAllowance(allowance: BigInt) =
    setBalanceAndAllowance(this.balance, allowance)

  def setBalanceAndAllowance(
      balance: BigInt,
      allowance: BigInt
    ) = this.synchronized {
    this.balance = balance
    this.allowance = allowance
    spendable = balance.min(allowance)

    var ordersToDelete = Set.empty[String]
    // we cancel older orders, not newer orders.
    while (spendable < reserved) {
      val one = reserves.head
      ordersToDelete += one.orderId
      spendable += one.reserved
      reserves = reserves.drop(1)
    }
    ordersToDelete
  }

  // Release balance/allowance for an order.
  def release(orderIds: Seq[String]): Set[String] = this.synchronized {

    val (toDelete, toKeep) = reserves.span(orderIds.contains)
    reserved += toDelete.map(_.reserved).sum
    reserves = toKeep

    // re-reserve the last one.

    toDelete.map(_.orderId).toSet
  }

  // def reserve(orderId: String): Set[String] = this.synchronized {
  //   if (!orderPool.contains(orderId)) Set.empty[String]
  //   else {
  //     indexMap.get(orderId) match {
  //       case Some(_) =>
  //         // in case tokenS == tokenB
  //         Set.empty[String]
  //       case None =>
  //         reservations :+= Reservation(orderId, 0, 0)
  //         rebalance()
  //     }
  //   }
  // }

  // // Release balance/allowance for an order.
  // def release(orderId: String): Set[String] = this.synchronized {
  //   indexMap.get(orderId) match {
  //     case None => Set.empty
  //     case Some(idx) =>
  //       reservations = reservations.patch(idx, Nil, 1)
  //       indexMap -= orderId
  //       // TODO(dongw): optimize cursor
  //       cursor = idx - 1 // Performance issue
  //       rebalance()
  //   }
  // }

  // // Rebalance due to change of an order.
  // def adjust(orderId: String): Set[String] = this.synchronized {
  //   indexMap.get(orderId) match {
  //     case None => Set.empty
  //     case Some(idx) =>
  //       assert(orderPool.contains(orderId))
  //       val order = orderPool(orderId)
  //       cursor = idx - 1
  //       rebalance()
  //   }
  // }

}
