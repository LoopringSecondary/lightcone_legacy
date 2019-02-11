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

class MarketManagerImplSpec_Cancellation extends MarketAwareSpec {

  import OrderStatus._

  "MarketManager" should "not throw error if cancel unexist orders" in {
    marketManager.cancelOrder("1234") should be(None)
  }

  "MarketManager" should "be able to cancel existing order" +
    "and should put order inside the orderbook" in {
    var order = actualNotDust(sellGTO(100000, 101))
    (fakePendingRingPool.getOrderPendingAmountS _).when(*).returns(0)
    (fakeAggregator.getOrderbookUpdate _).when().returns(Orderbook.Update())

    val result = marketManager.submitOrder(order, 0)
    result should be(emptyMatchingResult(order, STATUS_PENDING))
    marketManager.getNumOfSellOrders() should be(1)

    marketManager.cancelOrder(order.id) should be(Some(Orderbook.Update()))

    marketManager.getNumOfSellOrders() should be(0)
    marketManager.getNumOfBuyOrders() should be(0)
    marketManager.getNumOfOrders() should be(0)
    marketManager.getSellOrders(3) should be(Nil)
  }
}
