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
import java.util.concurrent.atomic.AtomicInteger

// This class is not thread safe.
// olderOrdersHavePriority = true
// allowPartialReserve = true
private[core] final class ReserveManagerImpl(
    val token: String,
    enableTracing: Boolean = false
  )(
    implicit
    eventHandler: ReserveEventHandler)
    extends ReserveManager
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
  protected var block: Long = 0

  protected var reserves = List.empty[Reserve]

  def getReserves() = reserves

  val i = new AtomicInteger(0)

  def trace[T](name: String)(fn: => T): T = {
    if (!enableTracing) {
      fn
    } else {
      val j = i.updateAndGet(_ + 1)
      log.debug(s" ====== $token: $j > $name ")
      val result = fn
      log.debug(s" ====== $token: $j < $name")
      result
    }
  }

  @inline def getBlock() = block

  def getBalanceOfToken() =
    BalanceOfToken(
      token,
      balance,
      allowance,
      balance - reserved,
      allowance - reserved,
      reserves.size,
      block
    )

  def setBalance(
      block: Long,
      balance: BigInt
    ) =
    setBalanceAndAllowance(block, balance, this.allowance)

  def setAllowance(
      block: Long,
      allowance: BigInt
    ) =
    setBalanceAndAllowance(block, this.balance, allowance)

  def setBalanceAndAllowance(
      block: Long,
      balance: BigInt,
      allowance: BigInt
    ) = trace("setBalanceAndAllowance") {
    this.block = block
    this.balance = balance
    this.allowance = allowance
    spendable = balance.min(allowance)

    rebalance { (reserveMe, _) =>
      reserves.foreach { r =>
        reserveMe(r.orderId, r.requested, false, Some(r.reserved))
      }
    }
  }

  // Release balance/allowance for an order.
  def release(orderIds: Set[String]): Set[String] = trace("release") {
    rebalance { (reserveMe, deleteMe) =>
      reserves.foreach { r =>
        if (orderIds.contains(r.orderId)) {
          deleteMe(r.orderId)
        } else {
          reserveMe(r.orderId, r.requested, false, Some(r.reserved))
        }
      }
    }
  }

  def reserve(
      orderId: String,
      requestedAmount: BigInt
    ): Set[String] = trace("reserve") {
    rebalance { (reserveMe, _) =>
      assert(requestedAmount > 0)

      var prevReservedOpt: Option[BigInt] = None

      reserves.foreach { r =>
        if (r.orderId == orderId) {
          prevReservedOpt = Some(r.reserved)

          if (requestedAmount <= r.reserved) {
            reserveMe(r.orderId, requestedAmount, true, Some(r.reserved))
          }
        } else {
          reserveMe(r.orderId, r.requested, false, Some(r.reserved))
        }
      }
      prevReservedOpt match {
        case Some(amount) if amount >= requestedAmount =>
        case _ =>
          reserveMe(orderId, requestedAmount, true, prevReservedOpt)
      }
    }
  }

  def clearOrders(): Unit = trace("clearOrders") {
    reserved = 0
    reserves = Nil
  }

  private type RESERVE_METHOD =
    (String, BigInt, Boolean, Option[BigInt]) => Unit
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
        forceEventHandling: Boolean,
        prevRequestedOpt: Option[BigInt] = None
      ) = {
      val reserved_ = requested.min(spendable - reserved)
      if (reserved_ == 0) {
        deleteMe(orderId)
      } else {
        this.reserved += reserved_
        buf += Reserve(orderId, requested, reserved_)

        prevRequestedOpt match {
          case Some(prevReserved)
              if !forceEventHandling && prevReserved == reserved_ =>
          case _ =>
            eventHandler.onTokenReservedForOrder(
              block,
              orderId,
              token,
              reserved_
            )
        }
      }
    }

    func(reserveMe, deleteMe)

    reserves = buf.toList
    ordersToDelete.toSet
  }
}
