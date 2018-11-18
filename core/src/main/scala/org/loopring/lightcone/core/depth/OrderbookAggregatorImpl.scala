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

import org.loopring.lightcone.core.data._

class OrderbookAggregatorImpl(priceDecimals: Int)
  extends OrderbookAggregator {

  private val sells = new OrderbookSide.Sells(priceDecimals, 0, true)
  private val buys = new OrderbookSide.Buys(priceDecimals, 0, true)
  private val lastPrice: Double = 0

  def getXOrderbookUpdate(num: Int = 0): XOrderbookUpdate = {
    if (num == 0) XOrderbookUpdate(sells.takeUpdatedSlots, buys.takeUpdatedSlots)
    else XOrderbookUpdate(sells.getSlots(num), buys.getSlots(num))
  }

  def increaseSell(
    price: Double,
    amount: Double,
    total: Double
  ) = adjustAmount(true, true, price, amount, total)

  def decreaseSell(
    price: Double,
    amount: Double,
    total: Double
  ) = adjustAmount(true, false, price, amount, total)

  def increaseBuy(
    price: Double,
    amount: Double,
    total: Double
  ) = adjustAmount(false, true, price, amount, total)

  def decreaseBuy(
    price: Double,
    amount: Double,
    total: Double
  ) = adjustAmount(false, false, price, amount, total)

  def adjustAmount(
    isSell: Boolean,
    increase: Boolean,
    price: Double,
    amount: Double,
    total: Double
  ) {
    if (price > 0 && amount > 0 && total > 0) {
      val side = if (isSell) sells else buys
      if (increase) side.increase(price, amount, total)
      else side.decrease(price, amount, total)
    }
  }

  def reset() {
    sells.reset()
    buys.reset()
  }
}
