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

class MarketManagerImplSpec_MultipleMatches extends MarketAwareSpec {
  "MarketManager" should "skip non-profitable orders" in {
    val buy1 = actualNotDust(buyGTO(100, 100050)) // worst price
    val buy2 = actualNotDust(buyGTO(100, 100040))
    val buy3 = actualNotDust(buyGTO(100, 100030)) // best price

    (fakeDustOrderEvaluator.isMatchableDust _).when(*).returns(false)
    (fakePendingRingPool.getOrderPendingAmountS _).when(*).returns(0)
    (fakeAggregator.getXOrderbookUpdate _).when(0).returns(XOrderbookUpdate())

    marketManager.submitOrder(buy1, 0)
    marketManager.submitOrder(buy2, 0)
    marketManager.submitOrder(buy3, 0)

    marketManager.getBuyOrders(5) should be(Seq(
      buy3.copy(status = PENDING),
      buy2.copy(status = PENDING),
      buy1.copy(status = PENDING)
    ))

    val sell1 = actualNotDust(sellGTO(110000, 100))

    val ring3 = OrderRing(ExpectedFill(sell1, null), ExpectedFill(buy3, null))
    val ring2 = OrderRing(ExpectedFill(sell1, null), ExpectedFill(buy2, null))
    val ring1 = OrderRing(ExpectedFill(sell1, null), ExpectedFill(buy1, null))

    (fackRingMatcher.matchOrders(_: Order, _: Order, _: Double))
      .when(*, *, *)
      .returns(Right(ring3))
      .noMoreThanOnce()

    (fackRingMatcher.matchOrders(_: Order, _: Order, _: Double))
      .when(*, *, *)
      .returns(Right(ring2))
      .noMoreThanOnce()

    (fackRingMatcher.matchOrders(_: Order, _: Order, _: Double))
      .when(*, *, *)
      .returns(Right(ring1))
      .noMoreThanOnce()

    // Submit a sell order as the take
    val result = marketManager.submitOrder(sell1, 0)

    result should be(MarketManager.MatchResult(
      Seq(ring3, ring2, ring1),
      sell1.copy(status = PENDING),
      XOrderbookUpdate()
    ))

    (fackRingMatcher.matchOrders(_: Order, _: Order, _: Double))
      .verify(*, *, *)
      .repeated(3)
  }

}
