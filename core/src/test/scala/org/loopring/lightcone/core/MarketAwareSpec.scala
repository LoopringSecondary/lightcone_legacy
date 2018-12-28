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

import org.loopring.lightcone.lib._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.core.depth._
import OrderStatus._
import ErrorCode._

trait MarketAwareSpec extends OrderAwareSpec {
  type MR = MarketManager.MatchResult

  implicit var timeProvider = new TimeProvider {
    def getTimeMillis = -1
  }

  var marketId = MarketId(primary = WETH, secondary = GTO)

  var fackRingMatcher: RingMatcher = _
  var fakeDustOrderEvaluator: DustOrderEvaluator = _
  var fakePendingRingPool: PendingRingPool = _
  var fakeAggregator: OrderAwareOrderbookAggregator = _
  var marketManager: MarketManager = _

  override def beforeEach() {
    nextId = 1
    fackRingMatcher = stub[RingMatcher]
    fakeDustOrderEvaluator = stub[DustOrderEvaluator]
    fakePendingRingPool = stub[PendingRingPool]
    fakeAggregator = stub[OrderAwareOrderbookAggregator]

    marketManager = new MarketManagerImpl(
      marketId,
      tm,
      fackRingMatcher,
      fakePendingRingPool,
      fakeDustOrderEvaluator,
      fakeAggregator
    )
  }

  def actualNotDust(order: Matchable): Matchable = {
    val o = order.copy(_actual = Some(order.original))
    (fakeDustOrderEvaluator.isOriginalDust _).when(o).returns(false)
    (fakeDustOrderEvaluator.isActualDust _).when(o).returns(false)
    o
  }

  def emptyMatchingResult(
      order: Matchable,
      newStatus: OrderStatus
    ) =
    MarketManager.MatchResult(
      Nil,
      order.copy(status = newStatus),
      Orderbook.Update()
    )

  def noMatchingActivity() = {
    (fackRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .verify(*, *, *)
      .never
  }

}
