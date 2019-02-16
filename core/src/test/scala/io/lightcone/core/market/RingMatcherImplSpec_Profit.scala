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

class RingMatcherImplSpec_Profit extends testing.CommonSpec {

  import ErrorCode._

  val nonProfitable = new RingIncomeEvaluator {
    def getRingIncome(ring: MatchableRing) = 0

    def isProfitable(
        ring: MatchableRing,
        fiatValueThreshold: Double
      ) = false
  }

  val alwaysProfitable = new RingIncomeEvaluator {
    def getRingIncome(ring: MatchableRing) = Long.MaxValue

    def isProfitable(
        ring: MatchableRing,
        fiatValueThreshold: Double
      ) = true
  }


  val maker =
    (Addr() |> "100000000000".dai --> "10000000".weth -- "10".lrc).matchableAsOriginal
  val taker =
    (Addr() |> "10000000".weth --> "100000000000".dai -- "10".lrc).matchableAsOriginal
  "RingMatcherImpl" should "not match orders if the ring is not profitable" in {
    implicit val rie = nonProfitable
    val matcher = new RingMatcherImpl()
    matcher.matchOrders(taker, maker, 0) should be(
      Left(ERR_MATCHING_INCOME_TOO_SMALL)
    )
  }

  "RingMatcherImpl" should "match orders if the ring is indeed profitable" in {
    implicit val rie = alwaysProfitable
    val matcher = new RingMatcherImpl()
    matcher.matchOrders(taker, maker, 0).isRight should be(true)
  }

  "RingMatcherImpl" should "not match orders if their `_matchable` fields are not set" in {
    implicit val rie = alwaysProfitable
    val matcher = new RingMatcherImpl()

    matcher
      .matchOrders(
        maker,
        taker
      )
      .isRight should be(true)

    // match the same orders with `_matchable`
    matcher.matchOrders(
      maker.copy(
        _matchable = Some(
          MatchableState()
        )
      ),
      taker
    ) should be(
      Left(ERR_MATCHING_TAKER_COMPLETELY_FILLED)
    )
  }

}
