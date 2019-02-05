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

import scala.collection.SortedMap

private[core] object OrderbookSide {

  import ErrorCode._

  class Sells(
      val priceDecimals: Int,
      val aggregationLevel: Int,
      val precisionForAmount: Int,
      val precisionForTotal: Int,
      val maintainUpdatedSlots: Boolean)
      extends LongOrderingSupport(true)
      with OrderbookSide

  class Buys(
      val priceDecimals: Int,
      val aggregationLevel: Int,
      val precisionForAmount: Int,
      val precisionForTotal: Int,
      val maintainUpdatedSlots: Boolean)
      extends LongOrderingSupport(false)
      with OrderbookSide
}

private[core] trait OrderbookSide {
  val isSell: Boolean
  val maintainUpdatedSlots: Boolean
  val priceDecimals: Int
  val aggregationLevel: Int
  val precisionForAmount: Int
  val precisionForTotal: Int
  implicit val ordering: Ordering[Long]

  import ErrorCode._

  val aggregationScaling = Math.pow(10, aggregationLevel)
  val priceScaling = Math.pow(10, priceDecimals)
  var slotMap = SortedMap.empty[Long, Orderbook.Slot]

  var oldSlots = Map.empty[Long, Orderbook.Slot]
  var updatedSlots = Map.empty[Long, Orderbook.Slot]

  def increase(
      price: Double,
      amount: Double,
      total: Double
    ): Unit =
    increase(Orderbook.Slot(getSlotForPriceId(price), amount, total))

  def decrease(
      price: Double,
      amount: Double,
      total: Double
    ): Unit =
    decrease(Orderbook.Slot(getSlotForPriceId(price), amount, total))

  def replace(
      price: Double,
      amount: Double,
      total: Double
    ): Unit =
    replace(Orderbook.Slot(getSlotForPriceId(price), amount, total))

  def increase(slot: Orderbook.Slot): Unit = adjustInternal(slot, _ + _)

  def decrease(slot: Orderbook.Slot): Unit = adjustInternal(slot, _ - _)

  def replace(slot: Orderbook.Slot): Unit =
    adjustInternal(slot, (old: Orderbook.Slot, new_ : Orderbook.Slot) => new_)

  def getDiff(slot: Orderbook.Slot) = {
    slot - slotMap.getOrElse(slot.slot, Orderbook.Slot(slot.slot, 0, 0))
  }

  private def adjustInternal(
      slot: Orderbook.Slot,
      op: (Orderbook.Slot, Orderbook.Slot) => Orderbook.Slot
    ) = {
    val id = getAggregationSlotFor(slot.slot)

    val old = slotMap.getOrElse(id, Orderbook.Slot(id, 0, 0))
    if (maintainUpdatedSlots && !oldSlots.contains(id)) {
      oldSlots += id -> old
    }

    var updated = op(old, slot.copy(slot = id))

    if (isSlotTooTiny(updated)) {
      updated = Orderbook.Slot(id, 0, 0)
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
      priceLimit: Option[Double]
    ): Seq[Orderbook.Slot] = {

    val items = priceLimit match {
      case None =>
        slotMap.values
      case Some(limit) =>
        if (isSell) {
          slotMap.values.dropWhile(_.slot < limit)
        } else {
          slotMap.values.dropWhile(_.slot > limit)
        }
    }
    items.filter(_.slot != 0).take(num).toList
  }

  def takeUpdatedSlots(): Seq[Orderbook.Slot] = {
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

  private[core] def getAggregationSlotFor(slot: Long) = {
    if (isSell)
      (Math.ceil(slot / aggregationScaling) * aggregationScaling).toLong
    else (Math.floor(slot / aggregationScaling) * aggregationScaling).toLong
  }

  private[core] def getSlotForPriceId(price: Double) = {
    if (isSell) Math.ceil(price * priceScaling).toLong
    else Math.floor(price * priceScaling).toLong
  }

  private val _amountScaling = Math.pow(10, precisionForAmount).toLong
  private val _totalScaling = Math.pow(10, precisionForTotal).toLong

  private def isSlotTooTiny(slot: Orderbook.Slot) = {
    (slot.amount * _amountScaling).toLong <= 0 ||
    (slot.total * _totalScaling).toLong <= 0
  }
}
