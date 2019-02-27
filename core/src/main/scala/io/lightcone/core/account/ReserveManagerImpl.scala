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

import io.lightcone.lib.TimeProvider
import org.slf4s.Logging
import scala.collection.mutable.ListBuffer
import java.util.concurrent.atomic.AtomicInteger

// This class is not thread safe.
// olderOrdersHavePriority = true
// allowPartialReserve = true
private[core] final class ReserveManagerImpl(
    val token: String,
    val refreshIntervalSeconds: Int,
    val enableTracing: Boolean = false
  )(
    implicit
    timeProvider: TimeProvider,
    eventHandler: ReserveEventHandler)
    extends ReserveManager
    with Logging {
  implicit private val t = token

  case class Reserve(
      orderId: String,
      requested: BigInt,
      allocated: BigInt)

  protected var allowance: BigInt = 0
  protected var balance: BigInt = 0
  protected var spendable: BigInt = 0
  protected var reserved: BigInt = 0
  protected var block: Long = 0
  protected var lastRefreshed: Long = 0

  protected var reserves = List.empty[Reserve]

  trait InternalOperator {

    def add(
        orderId: String,
        requested: BigInt,
        forceEventHandling: Boolean,
        prevRequestedOpt: Option[BigInt] = None
      ): Unit

    def remove(orderId: String): Unit
  }

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

  def needRefresh() =
    timeProvider.getTimeSeconds - lastRefreshed > refreshIntervalSeconds

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
    this.lastRefreshed = timeProvider.getTimeSeconds
    this.block = block
    this.balance = balance
    this.allowance = allowance
    this.spendable = balance.min(allowance)

    rebalance { operator =>
      reserves.foreach { r =>
        operator.add(r.orderId, r.requested, false, Some(r.allocated))
      }
    }
  }

  // Release balance/allowance for an order.
  def release(orderIds: Set[String]): Set[String] = trace("release") {
    rebalance { operator =>
      reserves.foreach { r =>
        if (orderIds.contains(r.orderId)) {
          operator.remove(r.orderId)
        } else {
          operator.add(r.orderId, r.requested, false, Some(r.allocated))
        }
      }
    }
  }

  def reserve(
      orderId: String,
      requestedAmount: BigInt
    ): Set[String] = trace("reserve") {
    rebalance { operator =>
      assert(requestedAmount > 0)

      var prevAllocatedOpt: Option[BigInt] = None

      reserves.foreach { r =>
        if (r.orderId == orderId) {
          prevAllocatedOpt = Some(r.allocated)

          if (requestedAmount <= r.allocated) {
            operator
              .add(r.orderId, requestedAmount, true, Some(r.allocated))
          }
        } else {
          operator.add(r.orderId, r.requested, false, Some(r.allocated))
        }
      }
      prevAllocatedOpt match {
        case Some(amount) if amount >= requestedAmount =>
        case _ =>
          operator.add(orderId, requestedAmount, true, prevAllocatedOpt)
      }
    }
  }

  def clearOrders(): Unit = trace("clearOrders") {
    reserved = 0
    reserves = Nil
  }

  private def rebalance(func: InternalOperator => Unit): Set[String] = {

    reserved = 0
    val ordersToDelete = ListBuffer.empty[String]
    val buf = ListBuffer.empty[Reserve]

    func(new InternalOperator {
      def remove(orderId: String): Unit = ordersToDelete += orderId

      def add(
          orderId: String,
          requested: BigInt,
          forceEventHandling: Boolean,
          prevRequestedOpt: Option[BigInt] = None
        ) = {
        val allocated = requested.min(spendable - reserved)

        if (allocated == 0) {
          remove(orderId)
        } else {
          reserved += allocated
          buf += Reserve(orderId, requested, allocated)

          prevRequestedOpt match {
            case Some(prevReserved)
                if !forceEventHandling && prevReserved == allocated =>
            case _ =>
              eventHandler
                .onTokenReservedForOrder(block, orderId, token, allocated)
          }
        }

      }

    })

    reserves = buf.toList
    ordersToDelete.toSet
  }
}
