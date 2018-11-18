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

class AccountTokenManagerImpl(
    val token: String,
    val maxNumOrders: Int = 1000
)(
    implicit
    orderPool: AccountOrderPool,
    dustEvaluator: DustOrderEvaluator
) extends AccountTokenManager with Logging {

  case class Reservation(
      orderId: String,
      accumulatedBalance: BigInt,
      accumulatedAllowance: BigInt
  )

  implicit private val _t = token

  private var balance: BigInt = 0
  private var allowance: BigInt = 0
  private var availableBalance: BigInt = 0
  private var availableAllowance: BigInt = 0

  def getBalance() = balance
  def getAllowance() = allowance
  def getAvailableBalance() = availableBalance
  def getAvailableAllowance() = availableAllowance

  // `cursor1 indicates the index to begin rebalancing
  private[account] var cursor: Int = -1
  // indexMap is the map of order id to order's index in `reservations`
  private[account] var indexMap = Map.empty[String, Int]
  private[account] var reservations = Seq.empty[Reservation]

  def size() = reservations.size
  def hasTooManyOrders() = size >= maxNumOrders

  // Initlize the balance and allowance and triger rebalancing.
  // Returns the ids of orders to delete

  def setBalance(balance: BigInt) =
    setBalanceAndAllowance(balance, this.allowance)

  def setAllowance(allowance: BigInt) =
    setBalanceAndAllowance(this.balance, allowance)

  def setBalanceAndAllowance(balance: BigInt, allowance: BigInt): Set[String] = {
    val cursor1 =
      if (balance >= this.balance) cursor
      else {
        val idx = reservations.indexWhere(_.accumulatedBalance > balance)
        if (idx == -1) cursor else idx - 1
      }

    val cursor2 = if (allowance >= this.allowance) {
      val idx = reservations.indexWhere { r ⇒
        val order = orderPool(r.orderId)
        order.reservedAmount != order.requestedAmount
      }
      if (idx == -1) cursor else idx - 1
    } else {
      val idx = reservations.indexWhere(_.accumulatedAllowance > allowance)
      if (idx == -1) cursor else idx - 1
    }

    cursor = Math.min(cursor1, cursor2)

    this.balance = balance
    this.allowance = allowance
    rebalance()
  }

  // Reserve balance/allowance for an order.
  def reserve(orderId: String): Set[String] = {
    if (!orderPool.contains(orderId)) Set.empty[String]
    else {
      indexMap.get(orderId) match {
        case Some(_) ⇒
          // in case tokenS == tokenB
          Set.empty[String]
        case None ⇒
          reservations :+= Reservation(orderId, 0, 0)
          rebalance()
      }
    }
  }

  // Release balance/allowance for an order.
  def release(orderId: String): Set[String] = {
    indexMap.get(orderId) match {
      case None ⇒ Set.empty
      case Some(idx) ⇒
        reservations = reservations.patch(idx, Nil, 1)
        indexMap -= orderId
        // TODO(dongw): optimize cursor
        cursor = idx - 1 // Performance issue
        rebalance()
    }
  }

  // Rebalance due to change of an order.
  def adjust(orderId: String): Set[String] = {
    indexMap.get(orderId) match {
      case None ⇒ Set.empty
      case Some(idx) ⇒
        assert(orderPool.contains(orderId))
        val order = orderPool(orderId)
        cursor = idx - 1
        rebalance()
    }
  }

  private def getAccumulatedAtCursor(): (BigInt, BigInt) = {
    if (cursor < 0) (0, 0)
    else {
      val r = reservations(cursor)
      (r.accumulatedBalance, r.accumulatedAllowance)
    }
  }

  private def rebalance(): Set[String] = {
    val (goodOnes, badOnes) = reservations.splitAt(cursor + 1)
    reservations = goodOnes

    var (accumulatedBalance, accumulatedAllowance) =
      getAccumulatedAtCursor()

    availableBalance = balance - accumulatedBalance
    availableAllowance = allowance - accumulatedAllowance

    var ordersToDelete = Set.empty[String]

    badOnes.foreach { r ⇒
      val order = orderPool(r.orderId)
      val requestedAmount = order.requestedAmount

      val status =
        if (availableBalance >= requestedAmount) XOrderStatus.PENDING
        else if (token == order.tokenS) XOrderStatus.CANCELLED_LOW_BALANCE
        else XOrderStatus.CANCELLED_LOW_FEE_BALANCE

      if (status != XOrderStatus.PENDING) {
        ordersToDelete += order.id
        indexMap -= order.id

        // delete order if they are still in the pool.
        orderPool.getOrder(order.id) map { order ⇒
          val updated = order.as(status)
          // log.debug("delete_by_status: " + updated)
          orderPool += updated
        }
      } else {
        val reserved =
          if (availableAllowance >= requestedAmount) requestedAmount
          else availableAllowance

        accumulatedBalance += requestedAmount
        accumulatedAllowance += reserved

        availableBalance = balance - accumulatedBalance
        availableAllowance = allowance - accumulatedAllowance

        indexMap += order.id -> reservations.size
        orderPool += order.withReservedAmount(reserved)
        reservations :+= Reservation(
          order.id,
          accumulatedBalance,
          accumulatedAllowance
        )
        cursor += 1
      }
    }

    log.trace("getDebugInfo: " + getDebugInfo)
    log.trace("ordersToDelete: " + ordersToDelete)
    ordersToDelete
  }

  private[core] def getDebugInfo() = {
    val localOrders = reservations.map(r ⇒ orderPool(r.orderId))
    (localOrders, reservations, indexMap, cursor)
  }
}
