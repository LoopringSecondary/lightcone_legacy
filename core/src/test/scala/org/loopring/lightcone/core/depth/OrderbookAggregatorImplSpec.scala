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
import org.loopring.lightcone.core.CommonSpec
import org.scalatest._

class OrderbookAggregatorImplSpec extends CommonSpec {
  var agg: OrderbookAggregator = _

  override def beforeEach() {
    agg = new OrderbookAggregatorImpl(5)
  }

  "OrderbookAggregatorImpl" should "not handle 0-valued adjustment" in {
    agg.increaseBuy(0, 1, 2)
    agg.getXOrderbookUpdate(0) should be(XOrderbookUpdate(Nil, Nil))

    agg.increaseBuy(1, 0, 2)
    agg.getXOrderbookUpdate(0) should be(XOrderbookUpdate(Nil, Nil))

    agg.increaseBuy(1, 2, 0)
    agg.getXOrderbookUpdate(0) should be(XOrderbookUpdate(Nil, Nil))
  }

  "OrderbookAggregatorImpl" should "not return unchanged slots" in {
    agg.increaseSell(0.987654321, 50, 5000)
    agg.decreaseSell(0.987654321, 50, 5000)
    agg.getXOrderbookUpdate(0) should be(XOrderbookUpdate(Nil, Nil))

    agg.increaseBuy(0.987654321, 50, 5000)
    agg.decreaseBuy(0.987654321, 50, 5000)
    agg.getXOrderbookUpdate(0) should be(XOrderbookUpdate(Nil, Nil))
  }

  "OrderbookAggregatorImpl" should "increase and decrease sell amounts correctly" in {
    agg.increaseSell(0.987654321, 50, 5000)
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(
      Seq(XOrderbookSlot(98766, 50, 5000)), Nil
    ))
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(Nil, Nil))

    agg.increaseSell(0.987651234, 1, 1)
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(
      Seq(XOrderbookSlot(98766, 51, 5001)), Nil
    ))
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(Nil, Nil))

    agg.increaseSell(0.1, 1, 1)
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(
      Seq(XOrderbookSlot(10000, 1, 1)), Nil
    ))
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(Nil, Nil))
    agg.getXOrderbookUpdate(3) should be(XOrderbookUpdate(
      Seq(XOrderbookSlot(10000, 1, 1), XOrderbookSlot(98766, 51, 5001)), Nil
    ))

    agg.decreaseSell(0.1, 2, 2)
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(
      Seq(XOrderbookSlot(10000, 0, 0)), Nil
    ))
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(Nil, Nil))

    agg.reset()
    agg.getXOrderbookUpdate(3) should be(XOrderbookUpdate(Nil, Nil))
  }

  "OrderbookAggregatorImpl" should "increase and decrease buy amounts correctly" in {
    agg.increaseBuy(0.123456789, 50, 5000)
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(
      Nil, Seq(XOrderbookSlot(12345, 50, 5000))
    ))
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(Nil, Nil))

    agg.increaseBuy(0.123456789, 1, 1)
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(
      Nil, Seq(XOrderbookSlot(12345, 51, 5001))
    ))
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(Nil, Nil))

    agg.increaseBuy(0.1, 1, 1)
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(
      Nil, Seq(XOrderbookSlot(10000, 1, 1))
    ))
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(Nil, Nil))

    agg.getXOrderbookUpdate(3) should be(XOrderbookUpdate(
      Nil, Seq(XOrderbookSlot(12345, 51, 5001), XOrderbookSlot(10000, 1, 1))
    ))

    agg.decreaseBuy(0.1, 2, 2)
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(
      Nil, Seq(XOrderbookSlot(10000, 0, 0))
    ))
    agg.getXOrderbookUpdate() should be(XOrderbookUpdate(Nil, Nil))

    agg.reset()
    agg.getXOrderbookUpdate(3) should be(XOrderbookUpdate(Nil, Nil))
  }

}
