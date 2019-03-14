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

import io.lightcone.core.testing._

class OrderbookAggregatorImplSpec extends CommonSpec {
  var agg: OrderbookAggregator = _

  override def beforeEach(): Unit = {
    implicit val marketPair = MarketPair(LRC, WETH)

    // TODO(dongw):
    implicit var metadataManager: MetadataManager = null
    agg = new OrderbookAggregatorImpl(5, 4, 4)
  }

  "OrderbookAggregatorImpl" should "not handle 0-valued adjustment" in {
    agg.adjustAmount(isSell = false, increase = true, 0, 1, 2)
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))

    agg.adjustAmount(isSell = false, increase = true, 1, 0, 2)
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))

    agg.adjustAmount(isSell = false, increase = true, 1, 2, 0)
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))
  }

  "OrderbookAggregatorImpl" should "not return unchanged slots" in {
    agg.adjustAmount(isSell = true, increase = true, 0.987654321, 50, 5000)
    agg.adjustAmount(isSell = true, increase = false, 0.987654321, 50, 5000)
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))

    agg.adjustAmount(isSell = false, increase = true, 0.987654321, 50, 5000)
    agg.adjustAmount(isSell = false, increase = false, 0.987654321, 50, 5000)
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))
  }

  "OrderbookAggregatorImpl" should "increase and decrease sell amounts correctly" in {
    agg.adjustAmount(isSell = true, increase = true, 0.987654321, 50, 5000)
    agg.getOrderbookUpdate() should be(
      Orderbook.InternalUpdate(Seq(Orderbook.Slot(98766, 50, 5000)), Nil)
    )
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))

    agg.adjustAmount(isSell = true, increase = true, 0.987651234, 1, 1)
    agg.getOrderbookUpdate() should be(
      Orderbook.InternalUpdate(Seq(Orderbook.Slot(98766, 51, 5001)), Nil)
    )
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))

    agg.adjustAmount(isSell = true, increase = true, 0.1, 1, 1)
    agg.getOrderbookUpdate() should be(
      Orderbook.InternalUpdate(Seq(Orderbook.Slot(10000, 1, 1)), Nil)
    )
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))
    agg.getOrderbookSlots(3) should be(
      Orderbook.InternalUpdate(
        Seq(Orderbook.Slot(10000, 1, 1), Orderbook.Slot(98766, 51, 5001)),
        Nil
      )
    )

    agg.adjustAmount(isSell = true, increase = false, 0.1, 2, 2)
    agg.getOrderbookUpdate() should be(
      Orderbook.InternalUpdate(Seq(Orderbook.Slot(10000, 0, 0)), Nil)
    )
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))

    agg.reset()
    agg.getOrderbookSlots(3) should be(Orderbook.InternalUpdate(Nil, Nil))
  }

  "OrderbookAggregatorImpl" should "increase and decrease buy amounts correctly" in {
    agg.adjustAmount(isSell = false, increase = true, 0.123456789, 50, 5000)
    agg.getOrderbookUpdate() should be(
      Orderbook.InternalUpdate(Nil, Seq(Orderbook.Slot(12345, 50, 5000)))
    )
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))

    agg.adjustAmount(isSell = false, increase = true, 0.123456789, 1, 1)
    agg.getOrderbookUpdate() should be(
      Orderbook.InternalUpdate(Nil, Seq(Orderbook.Slot(12345, 51, 5001)))
    )
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))

    agg.adjustAmount(isSell = false, increase = true, 0.1, 1, 1)
    agg.getOrderbookUpdate() should be(
      Orderbook.InternalUpdate(Nil, Seq(Orderbook.Slot(10000, 1, 1)))
    )
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))

    agg.getOrderbookSlots(3) should be(
      Orderbook.InternalUpdate(
        Nil,
        Seq(Orderbook.Slot(12345, 51, 5001), Orderbook.Slot(10000, 1, 1))
      )
    )

    agg.adjustAmount(isSell = false, increase = false, 0.1, 2, 2)
    agg.getOrderbookUpdate() should be(
      Orderbook.InternalUpdate(Nil, Seq(Orderbook.Slot(10000, 0, 0)))
    )
    agg.getOrderbookUpdate() should be(Orderbook.InternalUpdate(Nil, Nil))

    agg.reset()
    agg.getOrderbookSlots(3) should be(Orderbook.InternalUpdate(Nil, Nil))
  }

}
