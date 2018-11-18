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
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core._
import XOrderStatus._
import XMatchingFailure._

class MarketManagerImplSpec_Performance extends OrderAwareSpec {

  implicit var timeProvider = new TimeProvider {
    def getCurrentTimeMillis = -1
  }

  implicit val marketId = XMarketId(primary = WETH, secondary = GTO)
  var config = XMarketManagerConfig(
    maxNumbersOfOrders = 100, /* unsupported */
    priceDecimals = 5
  )

  implicit var fakeDustOrderEvaluator: DustOrderEvaluator = _
  implicit var fackRingIncomeEstimator: RingIncomeEstimator = _
  var marketManager: MarketManager = _

  override def beforeEach() {
    nextId = 1
    fackRingIncomeEstimator = stub[RingIncomeEstimator]
    fakeDustOrderEvaluator = stub[DustOrderEvaluator]

    marketManager = new MarketManagerImpl(
      marketId,
      config,
      new TokenMetadataManager,
      new RingMatcherImpl,
      new PendingRingPoolImpl,
      fakeDustOrderEvaluator,
      new OrderAwareOrderbookAggregatorImpl(config.priceDecimals)
    )

    (fakeDustOrderEvaluator.isOriginalDust _).when(*).returns(false)
    (fakeDustOrderEvaluator.isOutstandingDust _).when(*).returns(false)
    (fakeDustOrderEvaluator.isActualDust _).when(*).returns(false)
    (fakeDustOrderEvaluator.isMatchableDust _).when(*).returns(false)

    (fackRingIncomeEstimator.getRingIncome _).when(*).returns(1)
    (fackRingIncomeEstimator.isProfitable(_: OrderRing, _: Double)).when(*, *).returns(true)
  }

  "a" should "b" in {
    val now = System.currentTimeMillis
    val num = 100
    var rings = 0
    (1 to num) foreach { i ⇒
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
      -----------
      sells: ${sells.mkString("\n", "\n\n", "\n")}

      -----------
      buys: ${buys.mkString("\n", "\n\n", "\n")}

      """)

    println(s"""
      number of orders :${marketManager.getNumOfOrders}
      time cost for $num orders: $cost ($avg/second)
      num of rings: $rings
      """)

  }

  private def createGTOSellOrder(price: Double, amount: Double) = {
    val rawAmountS: BigInt = AmountConverter(GTO).displayToRaw(amount)
    val rawAmountB: BigInt = AmountConverter(WETH).displayToRaw(amount * price)
    sellGTO(rawAmountS, rawAmountB).withActualAsOriginal
  }

  private def createGTOBuyOrder(price: Double, amount: Double) = {
    val rawAmountS: BigInt = AmountConverter(GTO).displayToRaw(amount)
    val rawAmountB: BigInt = AmountConverter(WETH).displayToRaw(amount * price)
    buyGTO(rawAmountB, rawAmountS).withActualAsOriginal
  }

  private def createRandomOrder(price: Double, amount: Double) = {
    val rand = new util.Random()
    val p = price * (1 + (rand.nextInt % 5) / 100.0)
    val a = amount * (1 + (rand.nextInt % 20) / 100.0)
    if (rand.nextInt % 2 == 0) createGTOSellOrder(p, a) else createGTOBuyOrder(p, a)
  }

}

