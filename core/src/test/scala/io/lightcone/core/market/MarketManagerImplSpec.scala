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
import io.lightcone.lib._

trait MarketManagerImplSpec extends testing.CommonSpec {
  type MR = MarketManager.MatchResult

  implicit var timeProvider = new TimeProvider {
    def getTimeMillis = -1
  }

  var marketPair = MarketPair(GTO, WETH)
  var metadataManager: MetadataManager = null

  var fakeRingMatcher: RingMatcher = _
  var fakeDustOrderEvaluator: DustOrderEvaluator = _
  var fakePendingRingPool: PendingRingPool = _
  var fakeAggregator: OrderbookAggregator = _
  var marketManager: MarketManager = _

  override def beforeEach(): Unit = {
    // nextId = 1
    fakeRingMatcher = stub[RingMatcher]
    fakeDustOrderEvaluator = stub[DustOrderEvaluator]
    fakePendingRingPool = stub[PendingRingPool]
    fakeAggregator = stub[OrderbookAggregator]

    marketManager = new MarketManagerImpl(
      marketPair,
      metadataManager,
      fakeRingMatcher,
      fakePendingRingPool,
      fakeDustOrderEvaluator,
      fakeAggregator,
      100 // max matching attempts
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
      order.copy(_matchable = order._actual, status = newStatus)
    )

  def noMatchingActivity() = {
    (fakeRingMatcher
      .matchOrders(_: Matchable, _: Matchable, _: Double))
      .verify(*, *, *)
      .never
  }

}
