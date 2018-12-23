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
import org.loopring.lightcone.core.CommonSpec
import org.scalatest._

class OrderbookManagerImplSpec extends CommonSpec {
  var obm: OrderbookManager = _

  val config = XMarketConfig(
    levels = 2,
    priceDecimals = 5,
    precisionForAmount = 2,
    precisionForTotal = 1
  )

  override def beforeEach() {
    obm = new OrderbookManagerImpl(config)
  }

  "OrderbookManagerImplSpec" should "return empty order book after initialized" in {
    obm.getOrderbook(0, 100) should be(XOrderbook(0, Nil, Nil))
    obm.getOrderbook(1, 100) should be(XOrderbook(0, Nil, Nil))
  }

  "OrderbookManagerImplSpec" should "process very small slot" in {
    obm.processUpdate(
      XOrderbookUpdate(Seq(XOrderbookUpdate.XSlot(1, 10, 100)), Nil)
    )

    obm.getOrderbook(0, 100) should be(
      XOrderbook(0, Seq(XOrderbook.XItem("0.00001", "10.00", "100.0")), Nil)
    )

    obm.getOrderbook(1, 100) should be(
      XOrderbook(0, Seq(XOrderbook.XItem("0.0001", "10.00", "100.0")), Nil)
    )
  }

  "OrderbookManagerImplSpec" should "skip 0 value slots" in {
    obm.processUpdate(
      XOrderbookUpdate(
        Seq(XOrderbookUpdate.XSlot(0, 10, 100)),
        Seq(XOrderbookUpdate.XSlot(0, 10, 100))
      )
    )
    obm.getOrderbook(0, 100) should be(XOrderbook(0, Nil, Nil))
    obm.getOrderbook(1, 100) should be(XOrderbook(0, Nil, Nil))
  }

  "OrderbookManagerImplSpec" should "process sell slot and round up" in {
    obm.processUpdate(
      XOrderbookUpdate(Seq(XOrderbookUpdate.XSlot(12344, 10, 100)), Nil)
    )
    obm.getOrderbook(0, 100) should be(
      XOrderbook(0, Seq(XOrderbook.XItem("0.12344", "10.00", "100.0")), Nil)
    )

    obm.getOrderbook(1, 100) should be(
      XOrderbook(0, Seq(XOrderbook.XItem("0.1235", "10.00", "100.0")), Nil)
    )
  }

  "OrderbookManagerImplSpec" should "process buy slot and round down" in {
    obm.processUpdate(
      XOrderbookUpdate(Nil, Seq(XOrderbookUpdate.XSlot(12344, 10, 100)))
    )
    obm.getOrderbook(0, 100) should be(
      XOrderbook(0, Nil, Seq(XOrderbook.XItem("0.12344", "10.00", "100.0")))
    )

    obm.getOrderbook(1, 100) should be(
      XOrderbook(0, Nil, Seq(XOrderbook.XItem("0.1234", "10.00", "100.0")))
    )
  }

  "OrderbookManagerImplSpec" should "process sell slot with new lower values" in {
    obm.processUpdate(
      XOrderbookUpdate(
        Seq(
          XOrderbookUpdate.XSlot(12344, 10, 100),
          XOrderbookUpdate.XSlot(12345, 20, 200)
        ),
        Nil
      )
    )

    obm.processUpdate(
      XOrderbookUpdate(
        Seq(
          XOrderbookUpdate.XSlot(12344, 5, 40),
          XOrderbookUpdate.XSlot(12345, 10, 80)
        ),
        Nil
      )
    )

    obm.getOrderbook(0, 100) should be(
      XOrderbook(
        0,
        Seq(
          XOrderbook.XItem("0.12344", "5.00", "40.0"),
          XOrderbook.XItem("0.12345", "10.00", "80.0")
        ),
        Nil
      )
    )

    obm.getOrderbook(1, 100) should be(
      XOrderbook(0, Seq(XOrderbook.XItem("0.1235", "15.00", "120.0")), Nil)
    )
  }

  "OrderbookManagerImplSpec" should "process buy slot with new lower values" in {
    obm.processUpdate(
      XOrderbookUpdate(
        Nil,
        Seq(
          XOrderbookUpdate.XSlot(12344, 10, 100),
          XOrderbookUpdate.XSlot(12345, 20, 200)
        )
      )
    )

    obm.processUpdate(
      XOrderbookUpdate(
        Nil,
        Seq(
          XOrderbookUpdate.XSlot(12344, 5, 40),
          XOrderbookUpdate.XSlot(12345, 10, 80)
        )
      )
    )

    obm.getOrderbook(0, 100) should be(
      XOrderbook(
        0,
        Nil,
        Seq(
          XOrderbook.XItem("0.12345", "10.00", "80.0"),
          XOrderbook.XItem("0.12344", "5.00", "40.0")
        )
      )
    )

    obm.getOrderbook(1, 100) should be(
      XOrderbook(0, Nil, Seq(XOrderbook.XItem("0.1234", "15.00", "120.0")))
    )
  }

  "OrderbookManagerImplSpec" should "skip slots with lower or equal sell prices" in {
    obm.processUpdate(
      XOrderbookUpdate(
        Seq(
          XOrderbookUpdate.XSlot(12344, 10, 100),
          XOrderbookUpdate.XSlot(12345, 20, 200)
        ),
        Nil
      )
    )

    obm.processUpdate(
      XOrderbookUpdate(
        Seq(
          XOrderbookUpdate.XSlot(12344, 5, 40),
          XOrderbookUpdate.XSlot(12345, 10, 80)
        ),
        Nil
      )
    )

    obm.getOrderbook(0, 100) should be(
      XOrderbook(
        0,
        Seq(
          XOrderbook.XItem("0.12344", "5.00", "40.0"),
          XOrderbook.XItem("0.12345", "10.00", "80.0")
        ),
        Nil
      )
    )

    obm.getOrderbook(0, 100, Some(0.12343)) should be(
      XOrderbook(
        0.0,
        Seq(
          XOrderbook.XItem("0.12344", "5.00", "40.0"),
          XOrderbook.XItem("0.12345", "10.00", "80.0")
        ),
        Nil
      )
    )

    obm.getOrderbook(0, 100, Some(0.12344)) should be(
      XOrderbook(
        0.0,
        Seq(
          // XOrderbook.XItem("0.12344", "5.00", "40.0"),
          XOrderbook.XItem("0.12345", "10.00", "80.0")
        ),
        Nil
      )
    )

    obm.getOrderbook(0, 100, Some(0.123445)) should be(
      XOrderbook(0.0, Seq(XOrderbook.XItem("0.12345", "10.00", "80.0")), Nil)
    )

    obm.getOrderbook(0, 100, Some(0.12345)) should be(XOrderbook(0.0, Nil, Nil))
  }

  "OrderbookManagerImplSpec" should "skip slots with higer buy prices" in {
    obm.processUpdate(
      XOrderbookUpdate(
        Nil,
        Seq(
          XOrderbookUpdate.XSlot(12344, 10, 100),
          XOrderbookUpdate.XSlot(12345, 20, 200)
        )
      )
    )

    obm.processUpdate(
      XOrderbookUpdate(
        Nil,
        Seq(
          XOrderbookUpdate.XSlot(12344, 5, 40),
          XOrderbookUpdate.XSlot(12345, 10, 80)
        )
      )
    )

    obm.getOrderbook(0, 100) should be(
      XOrderbook(
        0,
        Nil,
        Seq(
          XOrderbook.XItem("0.12345", "10.00", "80.0"),
          XOrderbook.XItem("0.12344", "5.00", "40.0")
        )
      )
    )

    obm.getOrderbook(0, 100, Some(0.12346)) should be(
      XOrderbook(
        0.0,
        Nil,
        Seq(
          XOrderbook.XItem("0.12345", "10.00", "80.0"),
          XOrderbook.XItem("0.12344", "5.00", "40.0")
        )
      )
    )

    obm.getOrderbook(0, 100, Some(0.12345)) should be(
      XOrderbook(
        0.0,
        Nil,
        Seq(
          XOrderbook.XItem("0.12345", "10.00", "80.0"),
          XOrderbook.XItem("0.12344", "5.00", "40.0")
        )
      )
    )

    obm.getOrderbook(0, 100, Some(0.123445)) should be(
      XOrderbook(0.0, Nil, Seq(XOrderbook.XItem("0.12344", "5.00", "40.0")))
    )

    obm.getOrderbook(0, 100, Some(0.12344)) should be(
      XOrderbook(0.0, Nil, Seq(XOrderbook.XItem("0.12344", "5.00", "40.0")))
    )

    obm.getOrderbook(0, 100, Some(0.123435)) should be(
      XOrderbook(0.0, Nil, Nil)
    )
  }

}
