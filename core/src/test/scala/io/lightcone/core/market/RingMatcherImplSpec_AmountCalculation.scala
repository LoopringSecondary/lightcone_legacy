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

import spire.math.Rational

class RingMatcherImplSpec_AmountCalculation extends MarketManagerImplSpec {

  implicit val alwaysProfitable = new RingIncomeEvaluator {
    def getRingIncome(ring: MatchableRing) = Long.MaxValue

    def isProfitable(
        ring: MatchableRing,
        fiatValueThreshold: Double
      ) = true
  }

  var matcher: RingMatcher = _
  override def beforeEach(): Unit = {
    matcher = new RingMatcherImpl()
  }

  "price just match" should "full fill if matchable is raw amount" in {
    val taker =
      (Addr() |> "100000000000".weth --> "10000000000".dai -- "10".lrc).matchableAsOriginal

    val maker =
      (Addr() |> "10000000000".dai --> "100000000000".weth -- "10".lrc).matchableAsOriginal

    val expectRing = MatchableRing(
      taker = ExpectedMatchableFill(
        order = taker.copy(_matchable = Some(MatchableState())),
        pending = taker.original,
        amountMargin = 0
      ),
      maker = ExpectedMatchableFill(
        order = maker.copy(_matchable = Some(MatchableState())),
        pending = maker.original,
        amountMargin = 0
      )
    )

    matcher.matchOrders(taker, maker, 0) should be(Right(expectRing))
  }

  "price just match" should "part fill if both matchables are half raw amount" in {
    def testScale(scale: Rational): Unit = {
      val taker =
        (Addr() |> "100000".weth --> "100".dai -- "10".lrc)

      val maker =
        (Addr() |> "100".dai --> "100000".weth -- "10".lrc)

      val res = matcher.matchOrders(
        taker.copy(_matchable = Some(taker.original.scaleBy(scale))),
        maker.copy(_matchable = Some(maker.original.scaleBy(scale))),
        0
      )

      val pending = maker.original.scaleBy(scale)
      val expectRing = MatchableRing(
        taker = ExpectedMatchableFill(
          order = taker.copy(_matchable = Some(MatchableState())),
          pending = taker.original.scaleBy(scale),
          amountMargin = 333
        ),
        maker = ExpectedMatchableFill(
          order = maker.copy(_matchable = Some(MatchableState(amountB = 333))),
          pending = pending.copy(amountB = pending.amountB - 333),
          amountMargin = 0
        )
      )
      res should be(Right(expectRing))
    }

    // testScale(Rational(1, 2))
    testScale(Rational(1, 3))
    // testScale(Rational(1, 5))
    // testScale(Rational(1, 7))
  }

  "price3 just match" should "part fill if both matchables are a third of raw amount" in {
    //taker.amountB的计算通过amountS，因为精度问题，所以taker.amountB会在amountS的精度后为0
    val maker = (Addr() |> "100000000000".dai --> "100000000000".weth -- "10".lrc)
    val taker = (Addr() |> "100000000000".weth --> "100000000000".dai -- "10".lrc)

    val res = matcher.matchOrders(
      taker.copy(_matchable = Some(taker.original.scaleBy(1.0 / 3))),
      maker.copy(_matchable = Some(maker.original.scaleBy(1.0 / 3))),
      0
    )
    val expectRing = MatchableRing(
      taker = ExpectedMatchableFill(
        order = taker.copy(_matchable = Some(MatchableState())),
        pending = taker.original.scaleBy(1.0 / 3),
        amountMargin = 0
      ),
      maker = ExpectedMatchableFill(
        order = maker.copy(
          _matchable = Some(MatchableState())
        ),
        pending = maker.original.scaleBy(1.0 / 3),
        amountMargin = 0
      )
    )
    res.right.toOption should be(Some(expectRing))
  }

  "price1 with margin" should " fill if matchable is raw amount" in {
    val maker = (Addr() |> "1500000000000".dai --> "90000000000".weth -- "90".lrc)
    val taker = (Addr() |> "10000000000".weth --> "100000000000".dai -- "10".lrc)

    val res = matcher.matchOrders(
      taker.copy(
        _matchable = Some(
          MatchableState(
            amountS = taker.amountS / 2,
            amountB = taker.amountB / 2,
            amountFee = taker.amountFee / 2
          )
        )
      ),
      maker.copy(
        _matchable = Some(
          MatchableState(
            amountS = maker.amountS / 2,
            amountB = maker.amountB / 2,
            amountFee = maker.amountFee / 2
          )
        )
      ),
      0
    )
    val expectRing = MatchableRing(
      taker = ExpectedMatchableFill(
        order = taker.copy(_matchable = Some(MatchableState())),
        pending = MatchableState(
          amountS = taker.amountS / 2,
          amountB = taker.amountB / 2,
          amountFee = taker.amountFee / 2
        ),
        amountMargin = BigInt("2000000000")
      ),
      maker = ExpectedMatchableFill(
        order = maker.copy(
          _matchable = Some(
            MatchableState(
              amountS = BigInt("700000000000"),
              amountB = BigInt("42000000000"),
              amountFee = BigInt("42")
            )
          )
        ),
        pending = MatchableState(
          amountS = BigInt("50000000000"),
          amountB = BigInt("3000000000"),
          amountFee = BigInt("3")
        ),
        amountMargin = 0
      )
    )
    res.right.toOption should be(Some(expectRing))
  }
}
