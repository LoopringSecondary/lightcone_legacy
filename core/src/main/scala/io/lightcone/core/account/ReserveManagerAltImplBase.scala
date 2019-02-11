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

private[core] abstract class ReserveManagerAltImplBase
    extends ReserveManagerAlt
    with Logging {

  implicit val token: String
  val eventHandler: ReserveEventHandler

  case class Reserve(
      orderId: String,
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

    var ordersToDelete = Set.empty[String]

    // we cancel older orders, not newer orders.
    while (spendable < reserved) {
      val first = reserves.head
      ordersToDelete += first.orderId
      reserved -= first.reserved
      reserves = reserves.tail
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
    toDelete.map(_.orderId).toSet
  }

  def clearOrders(): Unit = this.synchronized {
    reserved = 0
    reserves = Nil
  }
}
