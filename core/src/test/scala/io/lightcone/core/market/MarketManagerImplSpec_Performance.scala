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
import io.lightcone.lib._

class MarketManagerImplSpec_Performance extends OrderAwareSpec {

  import OrderStatus._
  import ErrorCode._

  implicit var timeProvider = new TimeProvider {
    def getTimeMillis = -1
  }

  implicit val marketPair = MarketPair(GTO, WETH)

  implicit var fakeDustOrderEvaluator: DustOrderEvaluator = _
  implicit var fackRingIncomeEvaluator: RingIncomeEvaluator = _
  var marketManager: MarketManager = _

  override def beforeEach() {
    super.beforeEach()
    nextId = 1
    fackRingIncomeEvaluator = stub[RingIncomeEvaluator]
    fakeDustOrderEvaluator = stub[DustOrderEvaluator]

    marketManager = new MarketManagerImpl(
      marketPair,
      tm,
      new RingMatcherImpl,
      new PendingRingPoolImpl,
      fakeDustOrderEvaluator,
      new OrderAwareOrderbookAggregatorImpl(
        priceDecimals = 5,
        precisionForAmount = 4,
        precisionForTotal = 4
      ),
      100 // max matching attempts
    )

    (fakeDustOrderEvaluator.isOriginalDust _).when(*).returns(false)
    (fakeDustOrderEvaluator.isOutstandingDust _).when(*).returns(false)
    (fakeDustOrderEvaluator.isActualDust _).when(*).returns(false)
    (fakeDustOrderEvaluator.isMatchableDust _).when(*).returns(false)

    (fackRingIncomeEvaluator.getRingIncome _).when(*).returns(1)
    (fackRingIncomeEvaluator
      .isProfitable(_: MatchableRing, _: Double))
      .when(*, *)
      .returns(true)
  }

  "MarketManagingImpl" should "match 100 orders per second per thread" in {
    val now = System.currentTimeMillis
    val num = 100
    var rings = 0
    (1 to num) foreach { i =>
      var result = marketManager.submitOrder(createGTOSellOrder(0.12, 5000), 0)
      rings += result.rings.size

      result = marketManager.submitOrder(createGTOBuyOrder(0.12, 5000), 0)
      rings += result.rings.size
    }
    val cost = System.currentTimeMillis - now
    val avg = 1000 * num / cost

    val sells = marketManager.getSellOrders(100)
    val buys = marketManager.getBuyOrders(100)

    println(s"""
      number of orders :${marketManager.getNumOfOrders}
      time cost for $num orders: $cost ($avg/second)
      num of rings: $rings
      """)
  }

  private def createGTOSellOrder(
      price: Double,
      amount: Double
    ) = {
    sellGTO(amount, amount * price).withActualAsOriginal
  }

  private def createGTOBuyOrder(
      price: Double,
      amount: Double
    ) = {
    buyGTO(amount, amount * price).withActualAsOriginal
  }

  private def createRandomOrder(
      price: Double,
      amount: Double
    ) = {
    val rand = new util.Random()
    val p = price * (1 + (rand.nextInt % 5) / 100.0)
    val a = amount * (1 + (rand.nextInt % 20) / 100.0)
    if (rand.nextInt % 2 == 0) createGTOSellOrder(p, a)
    else createGTOBuyOrder(p, a)
  }

}
