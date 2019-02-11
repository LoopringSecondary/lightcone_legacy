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

private[core] final class ReserveManagerAltAutoScaleImpl(
    val token: String
  )(
    implicit
    eventHandler: ReserveEventHandler)
    extends ReserveManagerAlt
    with Logging {
  implicit private val t = token

  case class Reserve(
      orderId: String,
      requested: BigInt,
      reserved: BigInt,
      timestamp: Long = System.currentTimeMillis)

  protected var allowance: BigInt = 0
  protected var balance: BigInt = 0
  protected var spendable: BigInt = 0
  protected var reserved: BigInt = 0

  protected var reserves = List.empty[Reserve]

  def getReserves() = reserves

  def getAccountInfo() =
    AccountInfo(
      token,
      balance,
      allowance,
      balance - reserved,
      allowance - reserved,
      reserves.size
    )

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
    rebalance()
  }

  def rebalance(needed: BigInt = 0): Set[String] = {
    var ordersToDelete = Set.empty[String]

    while (spendable - reserved < needed) {
      reserves match {
        case head :: tail =>
          reserved -= head.reserved
          if (spendable - reserved <= needed) {
            ordersToDelete += head.orderId
            reserves = tail
          } else {
            val available = head.requested.min(spendable - reserved - needed)
            val first = head.copy(reserved = available)
            eventHandler.onTokenReservedForOrder(
              first.orderId,
              token,
              first.reserved
            )
            reserves = first :: tail
          }
        case Nil =>
      }
    }
    ordersToDelete
  }

  // Release balance/allowance for an order.
  def release(orderIds: Seq[String]): Set[String] = this.synchronized {
    val (toDelete, toKeep) = reserves.partition { r =>
      orderIds.contains(r.orderId)
    }
    reserved -= toDelete.map(_.reserved).sum
    reserves = toKeep

    rebalance()
    toDelete.map(_.orderId).toSet
  }

  def reserve(
      orderId: String,
      requestedAmount: BigInt
    ): Set[String] = this.synchronized {
    assert(requestedAmount > 0)

    var ordersToDelete = Set.empty[String]
    def available = requestedAmount.min(spendable - reserved)

    var idx = reserves.indexWhere(_.orderId == orderId)

    if (idx < 0) {
      // this is a new order
      if (available == 0) {
        ordersToDelete += orderId
      } else {
        reserved += available
        reserves = reserves :+ Reserve(orderId, requestedAmount, available)
        eventHandler.onTokenReservedForOrder(orderId, token, available)
      }
    } else {
      // this is an existing order to scale down/up
      val r = reserves(idx)
      val pos = reserves.size - idx
      reserved -= r.reserved
      reserves = reserves.patch(idx, Nil, 1)

      ordersToDelete ++= rebalance(requestedAmount)
      val idx2 = (reserves.size - pos).max(0)
      val updated = Reserve(orderId, requestedAmount, available)
      reserves = reserves.patch(idx2, Seq(updated), 1)
    }
    ordersToDelete
  }

  def clearOrders(): Unit = this.synchronized {
    reserved = 0
    reserves = Nil
  }
}
