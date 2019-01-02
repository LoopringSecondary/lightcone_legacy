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
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core._
import OrderStatus._
import ErrorCode._

class MarketManagerImplSpec_SkipOrderMatching extends MarketAwareSpec {
  "MarketManager" should "skip non-profitable orders" in {
    val buy1 = actualNotDust(buyGTO(100 !, 100050 !, 0 !))
    val buy2 = actualNotDust(buyGTO(100 !, 100040 !, 0 !))
    val buy3 = actualNotDust(buyGTO(100 !, 100030 !, 0 !))

    (fakeDustOrderEvaluator.isMatchableDust _).when(*).returns(false)
    (fakePendingRingPool.getOrderPendingAmountS _).when(*).returns(0)
    (fakeAggregator.getOrderbookUpdate _).when(0).returns(Orderbook.Update())

    marketManager.submitOrder(buy1, 0)
    marketManager.submitOrder(buy2, 0)
    marketManager.submitOrder(buy3, 0)

    marketManager.getBuyOrders(5) should be(
      Seq(
        buy1.copy(status = STATUS_PENDING),
        buy2.copy(status = STATUS_PENDING),
        buy3.copy(status = STATUS_PENDING)
      )
    )

    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .when(*, buy1.asPending.withMatchableAsActual().withActualAsOriginal(), *)
      .returns(Left(ERR_MATCHING_INCOME_TOO_SMALL))

    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .when(*, buy2.asPending.withMatchableAsActual().withActualAsOriginal(), *)
      .returns(Left(ERR_MATCHING_INCOME_TOO_SMALL))

    val ring = MatchableRing(null, null)
    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .when(*, buy3.asPending.withMatchableAsActual().withActualAsOriginal(), *)
      .returns(Right(ring))

    // Submit a sell order as the taker
    val sell1 = actualNotDust(sellGTO(100 !, 110000 !, 0 !))
    var result = marketManager.submitOrder(sell1, 0)

    result = result.copy(
      orderbookUpdate = result.orderbookUpdate.copy(lastPrice = 0.0)
    )

    result should be(
      MarketManager.MatchResult(
        Seq(ring),
        sell1.copy(status = STATUS_PENDING),
        Orderbook.Update()
      )
    )

    marketManager.getSellOrders(100) should be(
      Seq(sell1.copy(status = STATUS_PENDING))
    )

    marketManager.getBuyOrders(5) should be(
      Seq(
        buy1.copy(status = STATUS_PENDING),
        buy2.copy(status = STATUS_PENDING),
        buy3.copy(status = STATUS_PENDING)
      )
    )

    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .verify(*, *, *)
      .repeated(3)
  }

}
