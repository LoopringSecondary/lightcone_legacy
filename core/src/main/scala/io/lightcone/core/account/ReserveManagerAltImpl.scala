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
import scala.collection.mutable.ListBuffer

// orderOrdersHavePriority = true
// allowPartialReserve = true

private[core] final class ReserveManagerAltImpl(
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
      reserved: BigInt)

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

    reserved = 0

    val ordersToDelete = ListBuffer.empty[String]
    val buf = ListBuffer.empty[Reserve]

    reserves.foreach { r =>
      val available = r.requested.min(spendable - reserved)
      if (available > 0) {
        if (r.reserved != available) {
          eventHandler.onTokenReservedForOrder(r.orderId, token, available)
        }
        reserved += available
        buf += r.copy(reserved = available)
      } else {
        ordersToDelete += r.orderId
      }
    }

    reserves = buf.toList
    ordersToDelete.toSet
  }

  // Release balance/allowance for an order.
  def release(orderIds: Set[String]): Set[String] = this.synchronized {
    reserved = 0
    val ordersToDelete = ListBuffer.empty[String]
    val buf = ListBuffer.empty[Reserve]

    reserves.foreach { r =>
      if (orderIds.contains(r.orderId)) {
        ordersToDelete += r.orderId
      } else if (r.reserved == r.requested) {
        reserved += r.reserved
        buf += r
      } else {
        val available = r.requested.min(spendable - reserved)
        assert(available > 0)
        if (r.reserved != available) {
          eventHandler.onTokenReservedForOrder(r.orderId, token, available)
        }
        reserved += available
        buf += r.copy(reserved = available)
      }
    }

    reserves = buf.toList
    ordersToDelete.toSet
  }

  def reserve(
      orderId: String,
      requestedAmount: BigInt
    ): Set[String] = this.synchronized {
    assert(requestedAmount > 0)

    reserved = 0
    val ordersToDelete = ListBuffer.empty[String]
    val buf = ListBuffer.empty[Reserve]

    reserves = reserves :+ Reserve(orderId, requestedAmount, 0)
    var found = false

    reserves.foreach { r =>
      if (r.orderId != orderId || !found) {
        val requested =
          if (r.orderId == orderId) {
            found = true
            requestedAmount
          } else {
            r.requested
          }
        val available = requested.min(spendable - reserved)
        if (available == 0) {
          ordersToDelete += r.orderId
        } else {
          if (r.reserved != available) {
            eventHandler.onTokenReservedForOrder(r.orderId, token, available)
          }
          reserved += available
          buf += Reserve(r.orderId, requested, available)
        }
      }
    }

    reserves = buf.toList
    ordersToDelete.toSet
  }

  def clearOrders(): Unit = this.synchronized {
    reserved = 0
    reserves = Nil
  }
}
