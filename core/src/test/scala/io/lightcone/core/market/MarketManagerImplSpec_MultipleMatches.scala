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

class MarketManagerImplSpec_MultipleMatches extends MarketAwareSpec {

  "MarketManager" should "skip non-profitable orders" in {
    val buy1 = actualNotDust(buyGTO(BigInt(100), BigInt(100050), 0)) // worst price
    val buy2 = actualNotDust(buyGTO(BigInt(100), BigInt(100040), 0))
    val buy3 = actualNotDust(buyGTO(BigInt(100), BigInt(100030), 0)) // best price

    (fakeDustOrderEvaluator.isMatchableDust _).when(*).returns(false)
    (fakePendingRingPool.getOrderPendingAmountS _).when(*).returns(0)
    (fakeAggregator.getOrderbookUpdate _).when().returns(Orderbook.Update())

    marketManager.submitOrder(buy1, 0)
    marketManager.submitOrder(buy2, 0)
    marketManager.submitOrder(buy3, 0)

    marketManager.getBuyOrders(5) should be(
      Seq(buy1.asPending, buy2.asPending, buy3.asPending)
    )

    val sell1 = actualNotDust(sellGTO(BigInt(110000), BigInt(100), 0))

    val ring1 = MatchableRing(
      ExpectedMatchableFill(sell1, null),
      ExpectedMatchableFill(buy1, null)
    )
    val ring2 = MatchableRing(
      ExpectedMatchableFill(sell1, null),
      ExpectedMatchableFill(buy2, null)
    )
    val ring3 = MatchableRing(
      ExpectedMatchableFill(sell1, null),
      ExpectedMatchableFill(buy3, null)
    )

    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .when(*, *, *)
      .returns(Right(ring3))
      .noMoreThanOnce()

    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .when(*, *, *)
      .returns(Right(ring2))
      .noMoreThanOnce()

    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .when(*, *, *)
      .returns(Right(ring1))
      .noMoreThanOnce()

    // Submit a sell order as the take
    var result = marketManager.submitOrder(sell1, 0)

    // remove the last price
    result = result.copy(
      orderbookUpdate = result.orderbookUpdate.copy(latestPrice = 0.0)
    )

    result should be(
      MarketManager
        .MatchResult(sell1.asPending, Seq(ring3, ring2, ring1))
    )

    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .verify(*, *, *)
      .repeated(3)
  }

}
