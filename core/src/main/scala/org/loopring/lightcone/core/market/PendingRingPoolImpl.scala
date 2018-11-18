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

package org.loopring.lightcone.core.market

import org.loopring.lightcone.core.data._
import org.loopring.lightcone.core.base._
import org.slf4s.Logging

object PendingRingPoolImpl {
  case class OrderInfo(
      pendingAmountS: BigInt = 0,
      ringIds: Set[String] = Set.empty
  ) {
    assert(pendingAmountS >= 0)

    def +(another: OrderInfo) = OrderInfo(
      (pendingAmountS + another.pendingAmountS).max(0),
      ringIds ++ another.ringIds
    )

    def -(another: OrderInfo) = OrderInfo(
      (pendingAmountS - another.pendingAmountS).max(0),
      ringIds ++ another.ringIds
    )
  }

  case class RingInfo(
      takerId: String,
      takerPendingAmountS: BigInt,
      makerId: String,
      makerPendingAmountS: BigInt,
      timestamp: Long
  )
}

class PendingRingPoolImpl()(implicit time: TimeProvider)
  extends PendingRingPool with Logging {

  import PendingRingPoolImpl._

  private[core] var orderMap = Map.empty[String, OrderInfo]
  private[core] var ringMap = Map.empty[String, RingInfo]

  def getOrderPendingAmountS(orderId: String): BigInt =
    orderMap.get(orderId).map(_.pendingAmountS).getOrElse(0)

  def deleteOrder(orderId: String) = {
    val result = orderMap.contains(orderId)
    orderMap -= orderId
    result
  }

  def deleteRing(ringId: String): Boolean =
    ringMap.get(ringId) match {
      case Some(ringInfo) ⇒
        ringMap -= ringId

        decrementOrderPendingAmountS(
          ringInfo.takerId,
          ringId,
          ringInfo.takerPendingAmountS
        )

        decrementOrderPendingAmountS(
          ringInfo.makerId,
          ringId,
          ringInfo.makerPendingAmountS
        )
        true
      case None ⇒ false
    }

  def hasRing(ringId: String) = ringMap.contains(ringId)

  def addRing(ring: OrderRing) = {
    ringMap.get(ring.id) match {
      case Some(_) ⇒
      case None ⇒
        ringMap += ring.id -> RingInfo(
          ring.taker.id,
          ring.taker.pending.amountS,
          ring.maker.id,
          ring.maker.pending.amountS,
          time.getCurrentTimeMillis()
        )

        incrementOrderPendingAmountS(
          ring.taker.id,
          ring.id,
          ring.taker.pending.amountS
        )

        incrementOrderPendingAmountS(
          ring.maker.id,
          ring.id,
          ring.maker.pending.amountS
        )

      // log.debug("pending_orders: " + orderMap.mkString("\n\t"))
    }
  }

  def deleteAllRings() {
    orderMap = Map.empty[String, OrderInfo]
    ringMap = Map.empty[String, RingInfo]
  }

  def deleteRingsBefore(timestamp: Long) = ringMap.filter {
    case (_, ringInfo) ⇒ ringInfo.timestamp < timestamp
  }.keys.foreach(deleteRing)

  def deleteRingsOlderThan(age: Long) =
    deleteRingsBefore(time.getCurrentTimeMillis - age)

  def deleteRingsContainingOrder(orderId: String) = ringMap.filter {
    case (_, ringInfo) ⇒
      ringInfo.takerId == orderId || ringInfo.makerId == orderId
  }.keys.foreach(deleteRing)

  // Private methods
  private def decrementOrderPendingAmountS(
    orderId: String,
    ringId: String,
    pendingAmountS: BigInt
  ) = {
    orderMap.get(orderId) foreach { orderInfo ⇒
      val updated = orderInfo - OrderInfo(pendingAmountS, Set(ringId))
      if (updated.pendingAmountS <= 0) orderMap -= orderId
      else orderMap += orderId -> updated
    }
  }

  def incrementOrderPendingAmountS(
    orderId: String,
    ringId: String,
    pendingAmountS: BigInt
  ) = {
    orderMap += orderId ->
      (orderMap.getOrElse(orderId, OrderInfo()) +
        OrderInfo(pendingAmountS, Set(ringId)))
  }

}
