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

// This class is not thread safe.
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
    ) = {
    this.balance = balance
    this.allowance = allowance
    spendable = balance.min(allowance)

    rebalance { (reserveMe, _) =>
      reserves.foreach { r =>
        reserveMe(r.orderId, r.requested, Some(r.reserved))
      }
    }
  }

  // Release balance/allowance for an order.
  def release(orderIds: Set[String]): Set[String] = rebalance {
    (reserveMe, deleteMe) =>
      reserves.foreach { r =>
        if (orderIds.contains(r.orderId)) {
          deleteMe(r.orderId)
        } else {
          reserveMe(r.orderId, r.requested, Some(r.reserved))
        }
      }
  }

  def reserve(
      orderId: String,
      requestedAmount: BigInt
    ): Set[String] = rebalance { (reserveMe, _) =>
    assert(requestedAmount > 0)

    println(s"====: $token $orderId $requestedAmount")

    var prevReservedOpt: Option[BigInt] = None

    reserves.foreach { r =>
      if (r.orderId == orderId) {
        prevReservedOpt = Some(r.reserved)
        // skip exsiting same-order
      } else {
        reserveMe(r.orderId, r.requested, Some(r.reserved))
      }
    }

    reserveMe(orderId, requestedAmount, prevReservedOpt)
  }

  def clearOrders(): Unit = {
    reserved = 0
    reserves = Nil
  }

  private type RESERVE_METHOD = (String, BigInt, Option[BigInt]) => Unit
  private type DELETE_METHOD = (String) => Unit

  private def rebalance(
      func: (RESERVE_METHOD, DELETE_METHOD) => Unit
    ): Set[String] = {

    this.reserved = 0
    val ordersToDelete = ListBuffer.empty[String]
    val buf = ListBuffer.empty[Reserve]

    def deleteMe(orderId: String): Unit = ordersToDelete += orderId

    def reserveMe(
        orderId: String,
        requested: BigInt,
        prevRequestedOpt: Option[BigInt] = None
      ) = {
      val available = requested.min(spendable - reserved)
      if (available == 0) {
        deleteMe(orderId)
      } else {
        this.reserved += available
        buf += Reserve(orderId, requested, available)
        prevRequestedOpt match {
          case Some(amount) if amount == available =>
          case _ =>
            eventHandler.onTokenReservedForOrder(orderId, token, available)
        }
      }
    }

    func(reserveMe, deleteMe)

    reserves = buf.toList
    println(s"==========>>>>> $token -> $reserves")
    ordersToDelete.toSet
  }
}
