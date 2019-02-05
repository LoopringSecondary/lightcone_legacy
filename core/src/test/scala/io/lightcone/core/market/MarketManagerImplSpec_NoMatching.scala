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
import io.lightcone.core.implicits._

/// import io.lightcone.proto._

import OrderStatus._

class MarketManagerImplSpec_NoMatching extends MarketAwareSpec {

  "MarketManager" should "reject orders whose original size is dust" in {
    var order = sellGTO(1000, 1)
    (fakeDustOrderEvaluator.isOriginalDust _).when(order).returns(true)

    val result = marketManager.submitOrder(order, 0)
    result should be(emptyMatchingResult(order, STATUS_DUST_ORDER))

    noMatchingActivity()
  }

  "MarketManager" should "reject orders whose actual size is dust" in {
    var order = sellGTO(1000, 1)
    (fakeDustOrderEvaluator.isOriginalDust _).when(order).returns(false)
    (fakeDustOrderEvaluator.isActualDust _).when(order).returns(true)

    val result = marketManager.submitOrder(order, 0)
    result should be(emptyMatchingResult(order, STATUS_COMPLETELY_FILLED))

    noMatchingActivity()
  }

  "MarketManager" should "accept sell orders" in {
    var order1 = actualNotDust(sellGTO(100000, 100))
    var order2 = actualNotDust(sellGTO(100000, 101))

    (fakePendingRingPool.getOrderPendingAmountS _).when(*).returns(0)
    (fakeAggregator.getOrderbookUpdate _).when().returns(Orderbook.Update())

    var result = marketManager.submitOrder(order1, 0)
    result should be(emptyMatchingResult(order1, STATUS_PENDING))

    result = marketManager.submitOrder(order2, 0)
    result should be(emptyMatchingResult(order2, STATUS_PENDING))

    noMatchingActivity()

    marketManager.getNumOfSellOrders() should be(2)
    marketManager.getNumOfBuyOrders() should be(0)
    marketManager.getNumOfOrders() should be(2)

    marketManager.getSellOrders(3) should be(
      Seq(order1.asPending, order2.asPending)
    )

    marketManager.getSellOrders(1) should be(Seq(order1.asPending))

    marketManager.getBuyOrders(100) should be(Nil)
  }

  "MarketManager" should "accept buy orders" in {
    var order1 = actualNotDust(buyGTO(100, 100000))
    var order2 = actualNotDust(buyGTO(101, 100000))

    (fakePendingRingPool.getOrderPendingAmountS _).when(*).returns(0)
    (fakeAggregator.getOrderbookUpdate _).when().returns(Orderbook.Update())

    var result = marketManager.submitOrder(order1, 0)
    result should be(emptyMatchingResult(order1, STATUS_PENDING))

    result = marketManager.submitOrder(order2, 0)
    result should be(emptyMatchingResult(order2, STATUS_PENDING))

    noMatchingActivity()

    marketManager.getNumOfSellOrders() should be(0)
    marketManager.getNumOfBuyOrders() should be(2)
    marketManager.getNumOfOrders() should be(2)

    marketManager.getBuyOrders(3) should be(
      Seq(order1.asPending, order2.asPending)
    )

    marketManager.getBuyOrders(1) should be(Seq(order1.asPending))

    marketManager.getSellOrders(100) should be(Nil)
  }
}
