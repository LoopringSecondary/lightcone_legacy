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
import spire.math.Rational

class RingMatcherImplSpec_AmountCalculation extends OrderAwareSpec {

  implicit val alwaysProfitable = new RingIncomeEvaluator {
    def getRingIncome(ring: MatchableRing) = Long.MaxValue

    def isProfitable(
        ring: MatchableRing,
        fiatValueThreshold: Double
      ) = true
  }
  val matcher = new RingMatcherImpl()

  val maker = sellDAI(100000000000.0, 10000000000.0, 10.0)
  val taker = buyDAI(100000000000.0, 10000000000.0, 10.0)

  // "price just match" should "full fill if matchable is raw amount" in {
  //   val res = matcher.matchOrders(
  //     taker.copy(_matchable = Some(taker.original)),
  //     maker.copy(_matchable = Some(maker.original))
  //   )
  //   val expectRing = MatchableRing(
  //     taker = ExpectedMatchableFill(
  //       order = taker.copy(_matchable = Some(MatchableState())),
  //       pending = taker.original,
  //       amountMargin = 0 !
  //     ),
  //     maker = ExpectedMatchableFill(
  //       order = maker.copy(_matchable = Some(MatchableState())),
  //       pending = maker.original,
  //       amountMargin = 0 !
  //     )
  //   )
  //   res.right.toOption should be(Some(expectRing))
  // }

  "price just match" should "part fill if both matchables are half raw amount" in {
    def testScale(scale: Rational): Unit = {
      val res = matcher.matchOrders(
        taker.copy(_matchable = Some(taker.original.scaleBy(scale))),
        maker.copy(_matchable = Some(maker.original.scaleBy(scale)))
      )
      val pending = maker.original.scaleBy(scale)
      val expectRing = MatchableRing(
        taker = ExpectedMatchableFill(
          order = taker.copy(_matchable = Some(MatchableState())),
          pending = taker.original.scaleBy(scale),
          amountMargin = 33 !
        ),
        maker = ExpectedMatchableFill(
          order = maker.copy(_matchable = Some(MatchableState(amountB = 33))),
          pending = pending.copy(amountB = pending.amountB - 33),
          amountMargin = 0 !
        )
      )
      res.right.toOption should be(Some(expectRing))
    }

    // testScale(Rational(1, 2))
    testScale(Rational(1, 3))
    // testScale(Rational(1, 5))
    // testScale(Rational(1, 7))
  }

  // "price3 just match" should "part fill if both matchables are a third of raw amount" in {
  //   //taker.amountB的计算通过amountS，因为精度问题，所以taker.amountB会在amountS的精度后为0
  //   val res = matcher.matchOrders(
  //     taker.copy(_matchable = Some(taker.original.scaleBy(1.0 / 3))),
  //     maker.copy(_matchable = Some(maker.original.scaleBy(1.0 / 3)))
  //   )
  //   val expectRing = MatchableRing(
  //     taker = ExpectedMatchableFill(
  //       order = taker.copy(_matchable = Some(MatchableState())),
  //       pending = taker.original.scaleBy(1.0 / 3),
  //       amountMargin = 0!
  //     ),
  //     maker = ExpectedMatchableFill(
  //       order =
  //         maker.copy(_matchable = Some(MatchableState(amountS = 3, amountFee = 1))),
  //       pending = maker.original.scaleBy(1.0 / 3),
  //       amountMargin = 0!
  //     )
  //   )
  //   res.right.toOption should be(Some(expectRing))
  // }

  // "price just match" should "part fill if one matchable is half of raw amount" in {
  //   val res = matcher.matchOrders(
  //     taker.copy(
  //       _matchable = Some(
  //         MatchableState(
  //           amountS = taker.amountS / 2,
  //           amountB = taker.amountB/2,
  //           amountFee = taker.amountFee / 2
  //         )
  //       )
  //     ),
  //     maker.copy(
  //       _matchable = Some(
  //         MatchableState(
  //           amountS = maker.amount,
  //           amountB = maker.amountB,
  //           amountFee = maker.amountFee
  //         )
  //       )
  //     )
  //   )

  //   val expectRing = MatchableRing(
  //     taker = ExpectedMatchableFill(
  //       order = taker.copy(_matchable = Some(MatchableState())),
  //       pending = MatchableState(
  //         amountS = taker.amountS / 2,
  //         amountB = taker.amountB/2,
  //         amountFee = taker.amountFee / 2
  //       ),
  //       amountMargin = 0!
  //     ),
  //     maker = ExpectedMatchableFill(
  //       order = maker.copy(
  //         _matchable = Some(
  //           MatchableState(
  //             amountS = maker.amount - taker.amountB/2,
  //             amountB = maker.amountB, - taker.amountS / 2,
  //             amountFee = Rational(maker.amountFee) - Rational(
  //               maker.amountFee * (maker.amount - taker.amountB/2),
  //               maker.amount
  //             )
  //           )
  //         )
  //       ),
  //       pending = MatchableState(
  //         amountS =
  //           taker.amountB/2,
  //         amountB = taker.amountS / 2,
  //         amountFee =
  //           Rational(maker.amountFee * (maker.amount - taker.amountB/2), maker.amount)
  //       ),
  //       amountMargin = 0!
  //     )
  //   )
  //   res.right.toOption should be(Some(expectRing))
  // }

  // "price2 just match" should "fill with different decimal and raw amount isn't divisible by matchable " in {
  //   val otherDecimal = decimal.substring(0, 8)
  //   info(decimal)
  //   info(otherDecimal)
  //   val (taker.amountS, taker.amountB, taker.amountFee) = (
  //     BigInt("11" + otherDecimal),
  //     BigInt("110" + decimal),
  //     BigInt("10" + decimal)
  //   )
  //   val (maker.amount, maker.amountB, maker.amountFee) = (
  //     BigInt("110" + decimal),
  //     BigInt("11" + otherDecimal),
  //     BigInt("10" + decimal)
  //   )
  //   val maker = sellDAI(maker.amount, maker.amountB, maker.amountFee)
  //   val taker = buyDAI(taker.amountS, taker.amountB, taker.amountFee)
  //   val res = matcher.matchOrders(
  //     taker.copy(
  //       _matchable = Some(
  //         MatchableState(
  //           amountS = BigInt("333333333"),
  //           amountB = BigInt("33333333300000000000"),
  //           amountFee = BigInt("3030303027272727272")
  //         )
  //       )
  //     ),
  //     maker.copy(
  //       _matchable = Some(
  //         MatchableState(
  //           amountS = BigInt("33333333333333333333"),
  //           amountB = BigInt("333333333"),
  //           amountFee = BigInt("3030303030303030303")
  //         )
  //       )
  //     )
  //   )
  //   val expectRing = MatchableRing(
  //     taker = ExpectedMatchableFill(
  //       order = taker.copy(_matchable = Some(MatchableState())),
  //       pending = MatchableState(
  //         amountS = BigInt("333333333"),
  //         amountB = BigInt("33333333300000000000"),
  //         amountFee = BigInt("3030303027272727272")
  //       ),
  //       amountMargin = 0!
  //     ),
  //     maker = ExpectedMatchableFill(
  //       order = maker.copy(
  //         _matchable = Some(
  //           MatchableState(
  //             amountS = BigInt("33333333333"),
  //             amountFee = BigInt("3030303031")
  //           )
  //         )
  //       ),
  //       pending = MatchableState(
  //         amountS = BigInt("33333333300000000000"),
  //         amountB = BigInt("333333333"),
  //         amountFee = BigInt("3030303027272727272")
  //       ),
  //       amountMargin = 0!
  //     )
  //   )
  //   res.right.toOption should be(Some(expectRing))
  // }

  // "price1 with margin" should " fill if matchable is raw amount" in {
  //   val (maker.amount, maker.amountB, maker.amountFee) = (
  //     BigInt("1500000000000" + decimal),
  //     BigInt("90000000000" + decimal),
  //     BigInt("90" + decimal)
  //   )
  //   val (taker.amountS, taker.amountB, taker.amountFee) = (
  //     BigInt("10000000000" + decimal),
  //     BigInt("100000000000" + decimal),
  //     BigInt("10" + decimal)
  //   )
  //   val maker = sellDAI(maker.amount, maker.amountB, maker.amountFee)
  //   val taker = buyDAI(taker.amountS, taker.amountB, taker.amountFee)

  //   val res = matcher.matchOrders(
  //     taker.copy(
  //       _matchable = Some(
  //         MatchableState(
  //           amountS = taker.amountS / 2,
  //           amountB = taker.amountB/2,
  //           amountFee = taker.amountFee / 2
  //         )
  //       )
  //     ),
  //     maker.copy(
  //       _matchable = Some(
  //         MatchableState(
  //           amountS = maker.amountS / 2,
  //           amountB = maker.amountB/2,
  //           amountFee = maker.amountFee / 2
  //         )
  //       )
  //     )
  //   )
  //   val expectRing = MatchableRing(
  //     taker = ExpectedMatchableFill(
  //       order = taker.copy(_matchable = Some(MatchableState())),
  //       pending = MatchableState(
  //         amountS = taker.amountS / 2,
  //         amountB = taker.amountB/2,
  //         amountFee = taker.amountFee / 2
  //       ),
  //       amountMargin = BigInt("2000000000" + decimal)
  //     ),
  //     maker = ExpectedMatchableFill(
  //       order = maker.copy(
  //         _matchable = Some(
  //           MatchableState(
  //             amountS = BigInt("700000000000" + decimal),
  //             amountB = BigInt("42000000000" + decimal),
  //             amountFee = BigInt("42" + decimal)
  //           )
  //         )
  //       ),
  //       pending = MatchableState(
  //         amountS = BigInt("50000000000" + decimal),
  //         amountB = BigInt("3000000000" + decimal),
  //         amountFee = BigInt("3" + decimal)
  //       ),
  //       amountMargin = 0!
  //     )
  //   )
  //   res.right.toOption should be(Some(expectRing))
  // }

  // "price with margin" should " fill if matchable is raw amount" in {
  //   val (maker.amount, maker.amountB, maker.amountFee) = (
  //     BigInt("1500000000000" + decimal),
  //     BigInt("90000000000" + decimal),
  //     BigInt("90" + decimal)
  //   )
  //   val (taker.amountS, taker.amountB, taker.amountFee) = (
  //     BigInt("10000000000" + decimal),
  //     BigInt("100000000000" + decimal),
  //     BigInt("10" + decimal)
  //   )
  //   val maker = sellDAI(maker.amount, maker.amountB, maker.amountFee)
  //   val taker = buyDAI(taker.amountS, taker.amountB, taker.amountFee)

  //   val res = matcher.matchOrders(
  //     taker.copy(
  //       _matchable = Some(
  //         MatchableState(
  //           amountS = taker.amountS,
  //           amountB = taker.amountB,
  //           amountFee = taker.amountFee
  //         )
  //       )
  //     ),
  //     maker.copy(
  //       _matchable = Some(
  //         MatchableState(
  //           amountS = maker.amount,
  //           amountB = maker.amountB,
  //           amountFee = maker.amountFee
  //         )
  //       )
  //     )
  //   )
  //   val expectRing = MatchableRing(
  //     taker = ExpectedMatchableFill(
  //       order = taker.copy(_matchable = Some(MatchableState())),
  //       pending = MatchableState(
  //         amountS = taker.amountS,
  //         amountB = taker.amountB,
  //         amountFee = taker.amountFee
  //       ),
  //       amountMargin = BigInt("4000000000" + decimal)
  //     ),
  //     maker = ExpectedMatchableFill(
  //       order = maker.copy(
  //         _matchable = Some(
  //           MatchableState(
  //             amountS = BigInt("1400000000000" + decimal),
  //             amountB = BigInt("84000000000" + decimal),
  //             amountFee = BigInt("84" + decimal)
  //           )
  //         )
  //       ),
  //       pending = MatchableState(
  //         amountS = BigInt("100000000000" + decimal),
  //         amountB = BigInt("6000000000" + decimal),
  //         amountFee = BigInt("6" + decimal)
  //       ),
  //       amountMargin = 0!
  //     )
  //   )
  //   res.right.toOption should be(Some(expectRing))

  // }
}
