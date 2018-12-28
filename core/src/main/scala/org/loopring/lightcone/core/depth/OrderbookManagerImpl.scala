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
import scala.collection.SortedMap

class OrderbookManagerImpl(config: MarketConfig) extends OrderbookManager {

  private[depth] val viewMap = (0 until config.levels).map { level =>
    level -> new View(level)
  }.toMap

  private var lastPrice: Double = 0

  def processUpdate(update: OrderbookUpdate) = this.synchronized {
    if (update.lastPrice > 0) {
      lastPrice = update.lastPrice
    }
    val diff = viewMap(0).getDiff(update)
    viewMap.values.foreach(_.processUpdate(diff))
  }

  def getOrderbook(
      level: Int,
      size: Int,
      price: Option[Double] = None
    ) = {
    val p = price match {
      case Some(p) if p > 0 => p
      case _                => lastPrice
    }

    viewMap.get(level) match {
      case Some(view) => view.getOrderbook(size, p)
      case None       => Orderbook(p, Nil, Nil)
    }
  }

  def reset() = this.synchronized {
    viewMap.values.foreach(_.reset)
  }

  private[depth] class View(aggregationLevel: Int) {

    private val priceFormat = s"%.${config.priceDecimals - aggregationLevel}f"
    private val amountFormat = s"%.${config.precisionForAmount}f"
    private val totalFormat = s"%.${config.precisionForTotal}f"

    private val sellSide =
      new OrderbookSide.Sells(config.priceDecimals, aggregationLevel, false)
      with ConverstionSupport

    private val buySide =
      new OrderbookSide.Buys(config.priceDecimals, aggregationLevel, false)
      with ConverstionSupport

    def processUpdate(update: OrderbookUpdate) {
      update.sells.foreach(sellSide.increase)
      update.buys.foreach(buySide.increase)
    }

    def getDiff(update: OrderbookUpdate) = {
      OrderbookUpdate(
        update.sells.map(sellSide.getDiff),
        update.buys.map(buySide.getDiff)
      )
    }

    def getOrderbook(
        size: Int,
        price: Double
      ) = {
      val priceOpt =
        if (price > 0) Some(price)
        else {
          val sellPrice = sellSide
            .getDepth(1, None)
            .headOption
            .map(_.price.toDouble)
            .getOrElse(Double.MaxValue)

          val buyPrice = buySide
            .getDepth(1, None)
            .headOption
            .map(_.price.toDouble)
            .getOrElse(0.0)

          Some((sellPrice + buyPrice) / 2)
        }

      Orderbook(
        lastPrice,
        sellSide.getDepth(size, priceOpt),
        buySide.getDepth(size, priceOpt)
      )
    }

    def reset() {
      sellSide.reset()
      buySide.reset()
    }

    trait ConverstionSupport { self: OrderbookSide =>
      private def slotToItem(slot: OrderbookUpdate.Slot) =
        Orderbook.Item(
          priceFormat.format(slot.slot / priceScaling),
          amountFormat.format(slot.amount),
          totalFormat.format(slot.total)
        )

      def getDepth(
          num: Int,
          latestPrice: Option[Double]
        ): Seq[Orderbook.Item] = {
        val latestPriceSlot = latestPrice.map { p =>
          (p * priceScaling).toLong
        }
        getSlots(num, latestPriceSlot).map(slotToItem(_))
      }
    }
  }
}
