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

class OrderbookManagerImplSpec extends CommonSpec {
  var obm: OrderbookManager = _

  val metadata = MarketMetadata(
    orderbookAggLevels = 2,
    priceDecimals = 5,
    precisionForAmount = 2,
    precisionForTotal = 1
  )

  override def beforeEach() {
    obm = new OrderbookManagerImpl(metadata)
  }

  "OrderbookManagerImplSpec" should "return empty order book after initialized" in {
    obm.getOrderbook(0, 100) should be(Orderbook(0, Nil, Nil))
    obm.getOrderbook(1, 100) should be(Orderbook(0, Nil, Nil))
  }

  "OrderbookManagerImplSpec" should "process very small slot" in {
    obm.processUpdate(Orderbook.Update(Seq(Orderbook.Slot(1, 10, 100)), Nil))

    obm.getOrderbook(0, 100) should be(
      Orderbook(0, Seq(Orderbook.Item("0.00001", "10.00", "100.0")), Nil)
    )

    obm.getOrderbook(1, 100) should be(
      Orderbook(0, Seq(Orderbook.Item("0.0001", "10.00", "100.0")), Nil)
    )
  }

  "OrderbookManagerImplSpec" should "skip 0 value slots" in {
    obm.processUpdate(
      Orderbook.Update(
        Seq(Orderbook.Slot(0, 10, 100)),
        Seq(Orderbook.Slot(0, 10, 100))
      )
    )
    obm.getOrderbook(0, 100) should be(Orderbook(0, Nil, Nil))
    obm.getOrderbook(1, 100) should be(Orderbook(0, Nil, Nil))
  }

  "OrderbookManagerImplSpec" should "process sell slot and round up" in {
    obm.processUpdate(
      Orderbook.Update(Seq(Orderbook.Slot(12344, 10, 100)), Nil)
    )
    obm.getOrderbook(0, 100) should be(
      Orderbook(0, Seq(Orderbook.Item("0.12344", "10.00", "100.0")), Nil)
    )

    obm.getOrderbook(1, 100) should be(
      Orderbook(0, Seq(Orderbook.Item("0.1235", "10.00", "100.0")), Nil)
    )
  }

  "OrderbookManagerImplSpec" should "process buy slot and round down" in {
    obm.processUpdate(
      Orderbook.Update(Nil, Seq(Orderbook.Slot(12344, 10, 100)))
    )
    obm.getOrderbook(0, 100) should be(
      Orderbook(0, Nil, Seq(Orderbook.Item("0.12344", "10.00", "100.0")))
    )

    obm.getOrderbook(1, 100) should be(
      Orderbook(0, Nil, Seq(Orderbook.Item("0.1234", "10.00", "100.0")))
    )
  }

  "OrderbookManagerImplSpec" should "process sell slot with new lower values" in {
    obm.processUpdate(
      Orderbook.Update(
        Seq(Orderbook.Slot(12344, 10, 100), Orderbook.Slot(12345, 20, 200)),
        Nil
      )
    )

    obm.processUpdate(
      Orderbook.Update(
        Seq(Orderbook.Slot(12344, 5, 40), Orderbook.Slot(12345, 10, 80)),
        Nil
      )
    )

    obm.getOrderbook(0, 100) should be(
      Orderbook(
        0,
        Seq(
          Orderbook.Item("0.12344", "5.00", "40.0"),
          Orderbook.Item("0.12345", "10.00", "80.0")
        ),
        Nil
      )
    )

    obm.getOrderbook(1, 100) should be(
      Orderbook(0, Seq(Orderbook.Item("0.1235", "15.00", "120.0")), Nil)
    )
  }

  "OrderbookManagerImplSpec" should "process buy slot with new lower values" in {
    obm.processUpdate(
      Orderbook.Update(
        Nil,
        Seq(Orderbook.Slot(12344, 10, 100), Orderbook.Slot(12345, 20, 200))
      )
    )

    obm.processUpdate(
      Orderbook.Update(
        Nil,
        Seq(Orderbook.Slot(12344, 5, 40), Orderbook.Slot(12345, 10, 80))
      )
    )

    obm.getOrderbook(0, 100) should be(
      Orderbook(
        0,
        Nil,
        Seq(
          Orderbook.Item("0.12345", "10.00", "80.0"),
          Orderbook.Item("0.12344", "5.00", "40.0")
        )
      )
    )

    obm.getOrderbook(1, 100) should be(
      Orderbook(0, Nil, Seq(Orderbook.Item("0.1234", "15.00", "120.0")))
    )
  }

  "OrderbookManagerImplSpec" should "skip slots with lower or equal sell prices" in {
    obm.processUpdate(
      Orderbook.Update(
        Seq(Orderbook.Slot(12344, 10, 100), Orderbook.Slot(12345, 20, 200)),
        Nil
      )
    )

    obm.processUpdate(
      Orderbook.Update(
        Seq(Orderbook.Slot(12344, 5, 40), Orderbook.Slot(12345, 10, 80)),
        Nil
      )
    )

    obm.getOrderbook(0, 100) should be(
      Orderbook(
        0,
        Seq(
          Orderbook.Item("0.12344", "5.00", "40.0"),
          Orderbook.Item("0.12345", "10.00", "80.0")
        ),
        Nil
      )
    )

    obm.getOrderbook(0, 100, Some(0.12343)) should be(
      Orderbook(
        0.0,
        Seq(
          Orderbook.Item("0.12344", "5.00", "40.0"),
          Orderbook.Item("0.12345", "10.00", "80.0")
        ),
        Nil
      )
    )

    obm.getOrderbook(0, 100, Some(0.12344)) should be(
      Orderbook(
        0.0,
        Seq(
          Orderbook.Item("0.12344", "5.00", "40.0"),
          Orderbook.Item("0.12345", "10.00", "80.0")
        ),
        Nil
      )
    )

    obm.getOrderbook(0, 100, Some(0.123445)) should be(
      Orderbook(0.0, Seq(Orderbook.Item("0.12345", "10.00", "80.0")), Nil)
    )

    obm.getOrderbook(0, 100, Some(0.12346)) should be(Orderbook(0.0, Nil, Nil))
  }

  "OrderbookManagerImplSpec" should "skip slots with higer buy prices" in {
    obm.processUpdate(
      Orderbook.Update(
        Nil,
        Seq(Orderbook.Slot(12344, 10, 100), Orderbook.Slot(12345, 20, 200))
      )
    )

    obm.processUpdate(
      Orderbook.Update(
        Nil,
        Seq(Orderbook.Slot(12344, 5, 40), Orderbook.Slot(12345, 10, 80))
      )
    )

    obm.getOrderbook(0, 100) should be(
      Orderbook(
        0,
        Nil,
        Seq(
          Orderbook.Item("0.12345", "10.00", "80.0"),
          Orderbook.Item("0.12344", "5.00", "40.0")
        )
      )
    )

    obm.getOrderbook(0, 100, Some(0.12346)) should be(
      Orderbook(
        0.0,
        Nil,
        Seq(
          Orderbook.Item("0.12345", "10.00", "80.0"),
          Orderbook.Item("0.12344", "5.00", "40.0")
        )
      )
    )

    obm.getOrderbook(0, 100, Some(0.12345)) should be(
      Orderbook(
        0.0,
        Nil,
        Seq(
          Orderbook.Item("0.12345", "10.00", "80.0"),
          Orderbook.Item("0.12344", "5.00", "40.0")
        )
      )
    )

    obm.getOrderbook(0, 100, Some(0.123445)) should be(
      Orderbook(0.0, Nil, Seq(Orderbook.Item("0.12344", "5.00", "40.0")))
    )

    obm.getOrderbook(0, 100, Some(0.12344)) should be(
      Orderbook(0.0, Nil, Seq(Orderbook.Item("0.12344", "5.00", "40.0")))
    )

    obm.getOrderbook(0, 100, Some(0.123435)) should be(Orderbook(0.0, Nil, Nil))
  }

}
