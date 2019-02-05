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

package org.loopring.lightcone.core

import org.loopring.lightcone.lib._

/// import org.loopring.lightcone.proto._

import org.slf4s.Logging

final case class OrderInfo(
    pendingAmountS: BigInt = 0,
    ringIds: Set[String] = Set.empty) {
  assert(pendingAmountS >= 0)

  def +(another: OrderInfo) =
    OrderInfo(
      (pendingAmountS + another.pendingAmountS).max(0),
      ringIds ++ another.ringIds
    )

  def -(another: OrderInfo) =
    OrderInfo(
      (pendingAmountS - another.pendingAmountS).max(0),
      ringIds -- another.ringIds
    )
}

final case class RingInfo(
    takerId: String,
    takerPendingAmountS: BigInt,
    makerId: String,
    makerPendingAmountS: BigInt,
    timestamp: Long)

class PendingRingPoolImpl()(implicit time: TimeProvider)
    extends PendingRingPool
    with Logging {

  private[core] var orderMap = Map.empty[String, OrderInfo]
  private[core] var ringMap = Map.empty[String, RingInfo]

  def reset() = {
    orderMap = Map.empty
    ringMap = Map.empty
  }

  def getOrderPendingAmountS(orderId: String): BigInt =
    orderMap.get(orderId).map(_.pendingAmountS).getOrElse(0)

  def hasRing(ringId: String) = ringMap.contains(ringId)

  def addRing(ring: MatchableRing): Boolean = {
    ringMap.get(ring.id) match {
      case Some(_) => false
      case None =>
        ringMap += ring.id -> RingInfo(
          ring.taker.id,
          ring.taker.pending.amountS,
          ring.maker.id,
          ring.maker.pending.amountS,
          time.getTimeMillis()
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
        true
    }
  }

  def deleteRing(ringId: String): Set[String] = {
    ringMap.get(ringId) match {
      case Some(ringInfo) =>
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
        Set(ringInfo.takerId, ringInfo.makerId)

      case None =>
        Set.empty[String]
    }
  }

  def deleteRingsBefore(timestamp: Long) = {
    ringMap.filter {
      case (_, ringInfo) => ringInfo.timestamp < timestamp
    }.keys.map(deleteRing).flatten.toSet
  }

  def deleteRingsOlderThan(ageInSeconds: Long) =
    deleteRingsBefore(time.getTimeMillis - ageInSeconds * 1000)

  // Private methods
  // Private methods
  private def decrementOrderPendingAmountS(
      orderId: String,
      ringId: String,
      pendingAmountS: BigInt
    ) = {
    orderMap.get(orderId) foreach { orderInfo =>
      val updated = orderInfo - OrderInfo(pendingAmountS, Set(ringId))
      if (updated.pendingAmountS == 0) orderMap -= orderId
      else orderMap += orderId -> updated
    }
  }

  private def incrementOrderPendingAmountS(
      orderId: String,
      ringId: String,
      pendingAmountS: BigInt
    ) = {
    orderMap += orderId ->
      (orderMap.getOrElse(orderId, OrderInfo()) +
        OrderInfo(pendingAmountS, Set(ringId)))
  }
}
