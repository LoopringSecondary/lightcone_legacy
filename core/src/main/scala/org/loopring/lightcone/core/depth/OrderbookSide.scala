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

package org.loopring.lightcone.core.depth

import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.ErrorCode._
import scala.collection.SortedMap

private[depth] object OrderbookSide {

  class Sells(
      val priceDecimals: Int,
      val aggregationLevel: Int,
      val maintainUpdatedSlots: Boolean)
      extends LongOrderingSupport(true)
      with OrderbookSide

  class Buys(
      val priceDecimals: Int,
      val aggregationLevel: Int,
      val maintainUpdatedSlots: Boolean)
      extends LongOrderingSupport(false)
      with OrderbookSide
}

private[depth] trait OrderbookSide {
  val isSell: Boolean
  val maintainUpdatedSlots: Boolean
  val priceDecimals: Int
  val aggregationLevel: Int
  implicit val ordering: Ordering[Long]

  val aggregationScaling = Math.pow(10, aggregationLevel)
  val priceScaling = Math.pow(10, priceDecimals)
  var slotMap = SortedMap.empty[Long, OrderbookUpdate.Slot]

  var oldSlots = Map.empty[Long, OrderbookUpdate.Slot]
  var updatedSlots = Map.empty[Long, OrderbookUpdate.Slot]

  def increase(
      price: Double,
      amount: Double,
      total: Double
    ): Unit =
    increase(OrderbookUpdate.Slot(getSlotForPriceId(price), amount, total))

  def decrease(
      price: Double,
      amount: Double,
      total: Double
    ): Unit =
    decrease(OrderbookUpdate.Slot(getSlotForPriceId(price), amount, total))

  def replace(
      price: Double,
      amount: Double,
      total: Double
    ): Unit =
    replace(OrderbookUpdate.Slot(getSlotForPriceId(price), amount, total))

  def increase(slot: OrderbookUpdate.Slot): Unit = adjustInternal(slot, _ + _)

  def decrease(slot: OrderbookUpdate.Slot): Unit = adjustInternal(slot, _ - _)

  def replace(slot: OrderbookUpdate.Slot): Unit =
    adjustInternal(
      slot,
      (old: OrderbookUpdate.Slot, new_ : OrderbookUpdate.Slot) => new_
    )

  def getDiff(slot: OrderbookUpdate.Slot) = {
    slot - slotMap.getOrElse(slot.slot, OrderbookUpdate.Slot(slot.slot, 0, 0))
  }

  private def adjustInternal(
      slot: OrderbookUpdate.Slot,
      op: (OrderbookUpdate.Slot, OrderbookUpdate.Slot) => OrderbookUpdate.Slot
    ) = {
    val id = getAggregationSlotFor(slot.slot)

    val old = slotMap.getOrElse(id, OrderbookUpdate.Slot(id, 0, 0))
    if (maintainUpdatedSlots && !oldSlots.contains(id)) {
      oldSlots += id -> old
    }

    var updated = op(old, slot.copy(slot = id))
    if (updated.amount <= 0 || updated.total <= 0) {
      updated = OrderbookUpdate.Slot(id, 0, 0)
      slotMap -= id
    } else {
      slotMap += id -> updated
    }
    if (maintainUpdatedSlots && old != updated) {
      updatedSlots += id -> updated
    }
  }

  def reset() = {
    slotMap = SortedMap.empty
    oldSlots = Map.empty
    updatedSlots = Map.empty
  }

  def getSlots(
      num: Int,
      latestPriceSlot: Option[Long]
    ): Seq[OrderbookUpdate.Slot] = {
    val items = latestPriceSlot match {
      case None => slotMap.values
      case Some(limit) =>
        if (isSell) {
          slotMap.values.dropWhile(_.slot <= limit)
        } else {
          slotMap.values.dropWhile(_.slot > limit)
        }
    }
    items.filter(_.slot != 0).take(num).toList
  }

  def takeUpdatedSlots(): Seq[OrderbookUpdate.Slot] = {
    if (!maintainUpdatedSlots) {
      throw ErrorException(
        ERR_MATCHING_INVALID_INTERNAL_STATE,
        "maintainUpdatedSlots is false"
      )
    }

    val slots = updatedSlots.filter {
      case (id, slot) => oldSlots(id) != slot
    }.values.toList

    oldSlots = Map.empty
    updatedSlots = Map.empty
    slots
  }

  private[depth] def getAggregationSlotFor(slot: Long) = {
    if (isSell)
      (Math.ceil(slot / aggregationScaling) * aggregationScaling).toLong
    else (Math.floor(slot / aggregationScaling) * aggregationScaling).toLong
  }

  private[depth] def getSlotForPriceId(price: Double) = {
    if (isSell) Math.ceil(price * priceScaling).toLong
    else Math.floor(price * priceScaling).toLong
  }
}
