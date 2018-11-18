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

package org.loopring.lightcone.core.market

import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.core._
import XOrderStatus._
import XMatchingFailure._

class MarketManagerImplSpec_BasicMatching extends MarketAwareSpec {

  "MarketManager" should "not generate ring when ring matcher returns INCOME_TOO_SMALL error " +
    "and should put order inside the orderbook" in {
      var sellOrder = actualNotDust(sellGTO(100000, 101)) // price =  100000/101.0 = 989.12
      var buyOrder = actualNotDust(sellGTO(100000, 100)) // price =  100000/100.0 = 1000.00

      (fakePendingRingPool.getOrderPendingAmountS _).when(*).returns(0)
      (fakeAggregator.getXOrderbookUpdate _).when(0).returns(XOrderbookUpdate())

      (fackRingMatcher.matchOrders(_: Order, _: Order, _: Double))
        .when(*, *, *)
        .returns(Left(INCOME_TOO_SMALL))

      val sellResult = marketManager.submitOrder(sellOrder, 1)
      sellResult should be(emptyMatchingResult(sellOrder, PENDING))

      val buyResult = marketManager.submitOrder(buyOrder, 2)
      buyResult should be(emptyMatchingResult(buyOrder, PENDING))
    }

  "MarketManager" should "not generate ring when ring matcher returns ORDERS_NOT_TRADABLE error " +
    "and should put order inside the orderbook" in {
      var sellOrder = actualNotDust(sellGTO(100000, 101)) // price =  100000/101.0 = 989.12
      var buyOrder = actualNotDust(buyGTO(100000, 100)) // price =  100000/100.0 = 1000.00

      (fakePendingRingPool.getOrderPendingAmountS _).when(*).returns(0)
      (fakeAggregator.getXOrderbookUpdate _).when(0).returns(XOrderbookUpdate())

      (fackRingMatcher.matchOrders(_: Order, _: Order, _: Double))
        .when(*, *, *)
        .returns(Left(ORDERS_NOT_TRADABLE))

      val sellResult = marketManager.submitOrder(sellOrder, 1)
      sellResult should be(emptyMatchingResult(sellOrder, PENDING))

      val buyResult = marketManager.submitOrder(buyOrder, 2)
      buyResult should be(emptyMatchingResult(buyOrder, PENDING))
    }

  "MarketManager" should "generate a ring for sell order as taker" in {
    var sellOrder = actualNotDust(sellGTO(100000, 101)) // price =  100000/101.0 = 989.12
    var buyOrder = actualNotDust(buyGTO(100000, 100)) // price =  100000/100.0 = 1000.00

    (fakePendingRingPool.getOrderPendingAmountS _).when(*).returns(0)
    (fakeAggregator.getXOrderbookUpdate _).when(0).returns(XOrderbookUpdate())

    val ring = OrderRing(null, null)
    (fackRingMatcher.matchOrders(_: Order, _: Order, _: Double))
      .when(*, *, *)
      .returns(Right(ring))

    val sellResult = marketManager.submitOrder(sellOrder, 1)
    sellResult should be(emptyMatchingResult(sellOrder, PENDING))

    val buyResult = marketManager.submitOrder(buyOrder, 2)
    buyResult should be(MarketManager.MatchResult(
      Seq(ring),
      buyOrder.asPending,
      XOrderbookUpdate()
    ))

    marketManager.getSellOrders(100) should be(Seq(
      sellOrder.asPending
    ))

    marketManager.getBuyOrders(100) should be(Seq(
      buyOrder.asPending
    ))

    (fakePendingRingPool.addRing _).verify(ring).once
  }

  "MarketManager" should "generate a ring for buy order as taker" in {
    var buyOrder = actualNotDust(buyGTO(100000, 100)) // price =  100000/100.0 = 1000.00
    var sellOrder = actualNotDust(sellGTO(100000, 101)) // price =  100000/101.0 = 989.12

    (fakePendingRingPool.getOrderPendingAmountS _).when(*).returns(0)
    (fakeAggregator.getXOrderbookUpdate _).when(0).returns(XOrderbookUpdate())

    val ring = OrderRing(null, null)
    (fackRingMatcher.matchOrders(_: Order, _: Order, _: Double))
      .when(*, *, *)
      .returns(Right(ring))

    val buyResult = marketManager.submitOrder(buyOrder, 1)
    buyResult should be(emptyMatchingResult(buyOrder, PENDING))

    val sellResult = marketManager.submitOrder(sellOrder, 2)
    sellResult should be(MarketManager.MatchResult(
      Seq(ring),
      sellOrder.asPending,
      XOrderbookUpdate()
    ))

    marketManager.getSellOrders(100) should be(Seq(sellOrder.asPending))
    marketManager.getBuyOrders(100) should be(Seq(buyOrder.asPending))

    (fakePendingRingPool.addRing _).verify(ring).once
  }

}
