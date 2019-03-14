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

class OrderbookManagerImpl(metadata: MarketMetadata)
    extends OrderbookManager
    with Logging {

  private[core] val viewMap = (0 until metadata.orderbookAggLevels).map {
    level =>
      level -> new View(level)
  }.toMap

  private var latestPrice: Double = 0

  def processInternalUpdate(
      update: Orderbook.InternalUpdate
    ): Seq[Orderbook.Update] =
    this.synchronized {
      if (update.latestPrice > 0) {
        latestPrice = update.latestPrice
      }
      val diff = viewMap(0).getDiff(update)
      viewMap.values.map(_.processInternalUpdate(diff)).toSeq
    }

  def getOrderbook(
      level: Int,
      size: Int,
      price: Option[Double] = None
    ) = {
    val p = price match {
      case Some(p) if p > 0 => p
      case _                => latestPrice
    }

    viewMap.get(level) match {
      case Some(view) => view.getOrderbook(size, p)
      case None       => Orderbook(p, Nil, Nil)
    }
  }

  def reset() = this.synchronized {
    viewMap.values.foreach(_.reset)
  }

  private[core] class View(aggregationLevel: Int) {

    private val priceFormat = s"%.${metadata.priceDecimals - aggregationLevel}f"
    private val amountFormat = s"%.${metadata.precisionForAmount}f"
    private val totalFormat = s"%.${metadata.precisionForTotal}f"

    private val sellSide =
      new OrderbookSide.Sells(
        metadata.priceDecimals,
        aggregationLevel,
        metadata.precisionForAmount,
        metadata.precisionForTotal,
        false
      ) with ConverstionSupport

    private val buySide =
      new OrderbookSide.Buys(
        metadata.priceDecimals,
        aggregationLevel,
        metadata.precisionForAmount,
        metadata.precisionForTotal,
        false
      ) with ConverstionSupport

    def processInternalUpdate(
        update: Orderbook.InternalUpdate
      ): Orderbook.Update = {
      update.sells.foreach(sellSide.increase)
      update.buys.foreach(buySide.increase)
      Orderbook.Update(
        aggregationLevel,
        Nil, //updatedSellItems,
        Nil, // updatedBuyItems,
        latestPrice,
        metadata.marketPair
      )
    }

    def getDiff(update: Orderbook.InternalUpdate) = {
      Orderbook.InternalUpdate(
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

      val buys = buySide.getDepth(size, priceOpt)
      var sells = sellSide.getDepth(size + 1, priceOpt)
      // If the price is overlapping,we drop the top sell item
      sells =
        if (sells.headOption.isDefined &&
            sells.headOption.map(_.price) == buys.headOption.map(_.price)) {
          log.warn(s"order book overlapped ${buys} <> ${sells}")
          sells.drop(1)
        } else {
          sells.take(size)
        }

      Orderbook(latestPrice, sells, buys)
    }

    def reset(): Unit = {
      sellSide.reset()
      buySide.reset()
    }

    trait ConverstionSupport { self: OrderbookSide =>
      private def slotToItem(slot: Orderbook.Slot) =
        Orderbook.Item(
          priceFormat.format(slot.slot / priceScaling),
          amountFormat.format(slot.amount),
          totalFormat.format(slot.total)
        )

      def getDepth(
          num: Int,
          latestPrice: Option[Double]
        ): Seq[Orderbook.Item] = {

        val priceLimit = latestPrice.map(_ * priceScaling)
        getSlots(num, priceLimit).map(slotToItem(_))
      }
    }
  }
}
