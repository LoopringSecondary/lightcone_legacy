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

class RingMatcherImplSpec_Basic extends testing.CommonSpec {

  import ErrorCode._
  // import OrderStatus._

  implicit val alwaysProfitable = new RingIncomeEvaluator {
    def getRingIncome(ring: MatchableRing) = Long.MaxValue

    def isProfitable(
        ring: MatchableRing,
        fiatValueThreshold: Double
      ) = true
  }

  val matcher = new RingMatcherImpl()

  "RingMatcherImpl" should "not match untradable orders" in {
    val maker = (Addr() |> 1000.0.dai --> 1000001.0.weth).matchableAsOriginal
    val taker = (Addr() |> 1000000.0.weth --> 1000.0.dai).matchableAsOriginal

    matcher.matchOrders(taker, maker, 0) should be(
      Left(ERR_MATCHING_ORDERS_NOT_TRADABLE)
    )
  }

  "RingMatcherImpl" should "not match orders if one of them has tokenB as 0 " in {
    matcher.matchOrders(
      (Addr() |> 10.0.dai --> 0.0.weth).matchableAsOriginal,
      (Addr() |> 10.0.weth --> 100.0.dai).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_TAKER_ORDER))

    matcher.matchOrders(
      (Addr() |> 10.0.dai --> 1.0.weth).matchableAsOriginal,
      (Addr() |> 1.0.weth --> 0.0.dai).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_MAKER_ORDER))

    matcher.matchOrders(
      (Addr() |> 10.0.dai --> 0.0.weth).matchableAsOriginal,
      (Addr() |> 1.0.weth --> 0.0.dai).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_TAKER_ORDER))
  }

  "RingMatcherImpl" should "not match orders if one of them has tokenS as 0 " in {
    matcher.matchOrders(
      (Addr() |> .0.dai --> 10.0.weth).matchableAsOriginal,
      (Addr() |> 10.0.weth --> 10.0.dai).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_TAKER_ORDER))

    matcher.matchOrders(
      (Addr() |> 10.0.dai --> 10.0.weth).matchableAsOriginal,
      (Addr() |> .0.weth --> 10.0.dai).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_MAKER_ORDER))

    matcher.matchOrders(
      (Addr() |> .0.dai --> 10.0.weth).matchableAsOriginal,
      (Addr() |> .0.weth --> 10.0.dai).matchableAsOriginal
    ) should be(Left(ERR_MATCHING_INVALID_TAKER_ORDER))
  }

  "RingMatcherImpl" should "match two orders into a ring " in {
    val v1 = BigInt("60000000000000006000000000")
    val v2 = BigInt("50000000000000")

    val taker = Matchable(
      id = Addr(),
      tokenS = WETH,
      tokenB = GTO,
      tokenFee = LRC,
      amountS = v1,
      amountB = v2
    ).matchableAsOriginal

    val maker = Matchable(
      id = Addr(),
      tokenS = GTO,
      tokenB = WETH,
      tokenFee = LRC,
      amountS = v2,
      amountB = v1
    ).matchableAsOriginal
    matcher
      .matchOrders(taker, maker)
      .isRight should be(true)
  }
}
