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

package org.loopring.lightcone.core

import org.loopring.lightcone.proto._

import OrderStatus._
import ErrorCode._

class MarketManagerImplSpec_StopMatching extends MarketAwareSpec {
  "MarketManager" should "stop matching on the first price mismatch" in {
    val buy1 = actualNotDust(buyGTO(100, 100050)) // best price
    val buy2 = actualNotDust(buyGTO(100, 100040))
    val buy3 = actualNotDust(buyGTO(100, 100030)) // worst price

    (fakeDustOrderEvaluator.isMatchableDust _).when(*).returns(false)
    (fakePendingRingPool.getOrderPendingAmountS _).when(*).returns(0)
    (fakeAggregator.getOrderbookUpdate _).when().returns(Orderbook.Update())

    marketManager.submitOrder(buy1, 0)
    marketManager.submitOrder(buy2, 0)
    marketManager.submitOrder(buy3, 0)

    marketManager.getBuyOrders(5) should be(
      Seq(buy1.asPending, buy2.asPending, buy3.asPending)
    )

    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .when(*, buy1.asPending.withMatchableAsActual.withActualAsOriginal, *)
      .returns(Left(ERR_MATCHING_ORDERS_NOT_TRADABLE))

    val ring = MatchableRing(null, null)

    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .when(*, buy2.asPending.withMatchableAsActual.withActualAsOriginal, *)
      .returns(Right(ring))

    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .when(*, buy3.asPending.withMatchableAsActual.withActualAsOriginal, *)
      .returns(Right(ring))

    // Submit a sell order as the taker
    val sell1 = actualNotDust(sellGTO(100, 110000))
    val result = marketManager.submitOrder(sell1, 0)

    result should be(MarketManager.MatchResult(sell1.asPending))

    marketManager.getSellOrders(100) should be(Seq(sell1.asPending))

    marketManager.getBuyOrders(5) should be(
      Seq(buy1.asPending, buy2.asPending, buy3.asPending)
    )

    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .verify(*, *, *)
      .repeated(1)
  }

}
