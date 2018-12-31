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
import org.loopring.lightcone.proto._

trait OrderbookAggregator {
  def getOrderbookUpdate(num: Int = 0): Orderbook.Update

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
    )

  def reset(): Unit
}
