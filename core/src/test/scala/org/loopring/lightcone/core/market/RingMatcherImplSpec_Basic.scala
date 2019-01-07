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
import org.loopring.lightcone.core._
import org.loopring.lightcone.proto._
import org.scalatest._
import ErrorCode._

class RingMatcherImplSpec_Basic extends OrderAwareSpec {

  implicit val alwaysProfitable = new RingIncomeEstimator {
    def getRingIncome(ring: MatchableRing) = Long.MaxValue

    def isProfitable(
        ring: MatchableRing,
        fiatValueThreshold: Double
      ) = true
  }

  val matcher = new RingMatcherImpl()

  "RingMatcherImpl" should "not match untradable orders" in {
    val maker =
      sellDAI(BigInt(100000000), BigInt(100000001), 0).matchableAsOriginal
    val taker =
      buyDAI(BigInt(100000000), BigInt(100000000), 0).matchableAsOriginal

    matcher.matchOrders(taker, maker, 0) should be(
      Left(ERR_MATCHING_ORDERS_NOT_TRADABLE)
    )
  }

  "RingMatcherImpl" should "not match orders if one of them has tokenB as 0 " in {
    matcher.matchOrders(
      sellDAI(10, 0).matchableAsOriginal,
      buyDAI(10, 10).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_TAKER_ORDER))

    matcher.matchOrders(
      sellDAI(10, 10).matchableAsOriginal,
      buyDAI(10, 0).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_MAKER_ORDER))

    matcher.matchOrders(
      sellDAI(10, 0).matchableAsOriginal,
      buyDAI(10, 0).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_TAKER_ORDER))
  }

  "RingMatcherImpl" should "not match orders if one of them has tokenS as 0 " in {
    matcher.matchOrders(
      sellDAI(0, 10).matchableAsOriginal,
      buyDAI(10, 10).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_TAKER_ORDER))

    matcher.matchOrders(
      sellDAI(10, 10).matchableAsOriginal,
      buyDAI(0, 10).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_MAKER_ORDER))

    matcher.matchOrders(
      sellDAI(0, 10).matchableAsOriginal,
      buyDAI(0, 10).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_TAKER_ORDER))
  }

  "RingMatcherImpl" should "verify two orders in MarketManagerImplSpec_Performance should be matched in a ring " in {
    matcher
      .matchOrders(
        taker = Matchable(
          "taker",
          WETH,
          GTO,
          LRC,
          BigInt("60000000000000006000000000"),
          BigInt("50000000000000"),
          0,
          -1,
          0,
          OrderStatus.STATUS_PENDING,
          0.0,
          None,
          None,
          None,
          Some(MatchableState(0, 0, 0))
        ).matchableAsOriginal,
        maker = Matchable(
          "maker",
          GTO,
          WETH,
          LRC,
          BigInt("50000000000000"),
          BigInt("60000000000000006000000000"),
          0,
          -1,
          0,
          OrderStatus.STATUS_PENDING,
          0.0,
          None,
          None,
          None,
          Some(MatchableState(0, 0, 0))
        ).matchableAsOriginal
      )
      .isRight should be(true)
  }
}
