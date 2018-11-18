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
import scala.collection.SortedMap

class OrderbookManagerImpl(config: XOrderbookConfig)
  extends OrderbookManager {

  private[depth] val viewMap = (0 until config.levels).map {
    level ⇒ level -> new View(level)
  }.toMap

  def processUpdate(update: XOrderbookUpdate) = {
    val diff = viewMap(0).getDiff(update)
    viewMap.values.foreach(_.processUpdate(diff))
  }

  def getXOrderbook(level: Int, size: Int) = viewMap.get(level) match {
    case Some(view) ⇒ view.getXOrderbook(size)
    case None       ⇒ XOrderbook(Nil, Nil)
  }

  def reset() = viewMap.values.foreach(_.reset)

  private[depth] class View(aggregationLevel: Int) {

    private val priceFormat = s"%.${config.priceDecimals - aggregationLevel}f"
    private val amountFormat = s"%.${config.precisionForAmount}f"
    private val totalFormat = s"%.${config.precisionForTotal}f"

    private val sellSide = new OrderbookSide.Sells(
      config.priceDecimals, aggregationLevel, false
    ) with ConverstionSupport

    private val buySide = new OrderbookSide.Buys(
      config.priceDecimals, aggregationLevel, false
    ) with ConverstionSupport

    def processUpdate(update: XOrderbookUpdate) {
      update.sells.foreach(sellSide.increase)
      update.buys.foreach(buySide.increase)
    }

    def getDiff(update: XOrderbookUpdate) = {
      XOrderbookUpdate(
        update.sells.map(sellSide.getDiff),
        update.buys.map(buySide.getDiff)
      )
    }

    def getXOrderbook(size: Int) =
      XOrderbook(sellSide.getDepth(size), buySide.getDepth(size))

    def reset() {
      sellSide.reset()
      buySide.reset()
    }

    trait ConverstionSupport { self: OrderbookSide ⇒
      private def slotToItem(slot: XOrderbookSlot) =
        XOrderbookItem(
          priceFormat.format(slot.slot / priceScaling),
          amountFormat.format(slot.amount),
          totalFormat.format(slot.total)
        )

      def getDepth(num: Int): Seq[XOrderbookItem] =
        getSlots(num)
          .filter(_.slot != 0)
          .map(slotToItem(_))
    }
  }
}

