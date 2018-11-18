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

import org.loopring.lightcone.core.OrderAwareSpec
import org.loopring.lightcone.core.data._
import org.scalatest._
import XMatchingFailure._

class RingMatcherImplSpec_Profit extends OrderAwareSpec {

  val nonProfitable = new RingIncomeEstimator {
    def getRingIncome(ring: OrderRing) = 0
    def isProfitable(ring: OrderRing, fiatValueThreshold: Double) = false
  }

  val alwaysProfitable = new RingIncomeEstimator {
    def getRingIncome(ring: OrderRing) = Long.MaxValue
    def isProfitable(ring: OrderRing, fiatValueThreshold: Double) = true
  }

  val maker = sellDAI(10, 10).matchableAsOriginal
  val taker = buyDAI(10, 10).matchableAsOriginal

  "RingMatcherImpl" should "not match orders if the ring is not profitable" in {
    implicit val rie = nonProfitable
    val matcher = new RingMatcherImpl()
    matcher.matchOrders(taker, maker, 0) should be(Left(INCOME_TOO_SMALL))
  }

  "RingMatcherImpl" should "match orders if the ring is indeed profitable" in {
    implicit val rie = alwaysProfitable
    val matcher = new RingMatcherImpl()
    matcher.matchOrders(taker, maker, 0).isRight should be(true)
  }

  "RingMatcherImpl" should "not match orders if their `_matchable` fields are not set" in {
    implicit val rie = alwaysProfitable
    val matcher = new RingMatcherImpl()

    matcher.matchOrders(
      sellDAI(10, 10).matchableAsOriginal,
      buyDAI(10, 10).matchableAsOriginal
    ).isRight should be(true)

    // match the same orders with `_matchable`
    matcher.matchOrders(
      sellDAI(10, 10),
      buyDAI(10, 10)
    ) should be(Left(TAKER_COMPLETELY_FILLED))
  }

}
