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

class RingMatcherImplSpec_AmountCalculation extends OrderAwareSpec {

  implicit val alwaysProfitable = new RingIncomeEstimator {
    def getRingIncome(ring: OrderRing) = Long.MaxValue

    def isProfitable(ring: OrderRing, fiatValueThreshold: Double) = true
  }
  val matcher = new RingMatcherImpl()
  var decimal = "000000000000000000000000000000".substring(0, 18)
  val (makerAmountS, makerAmountB, makerFee) = (
    BigInt("100000000000" + decimal),
    BigInt("10000000000" + decimal),
    BigInt("10" + decimal)
  )
  val (takerAmountS, takerAmountB, takerFee) = (
    BigInt("10000000000" + decimal),
    BigInt("100000000000" + decimal),
    BigInt("10" + decimal)
  )
  val maker = sellDAI(makerAmountS, makerAmountB, makerFee)
  val taker = buyDAI(takerAmountS, takerAmountB, takerFee)

  "price just match" should "full fill if matchable is raw amount" in {
    val res = matcher.matchOrders(
      taker.copy(_matchable = Some(OrderState(amountS = takerAmountS, amountB = takerAmountB, amountFee = takerFee))),
      maker.copy(_matchable = Some(OrderState(amountS = makerAmountS, amountB = makerAmountB, amountFee = makerFee)))
    )
    val expectRing = OrderRing(
      taker = ExpectedFill(
        order = taker.copy(_matchable = Some(OrderState())),
        pending = OrderState(amountS = takerAmountS, amountB = takerAmountB, amountFee = takerFee),
        amountMargin = BigInt(0)
      ),
      maker = ExpectedFill(
        order = maker.copy(_matchable = Some(OrderState())),
        pending = OrderState(amountS = makerAmountS, amountB = makerAmountB, amountFee = makerFee),
        amountMargin = BigInt(0)
      )
    )
    res.right.toOption should be(Some(expectRing))
  }

  "price just match" should "part fill if both matchables are half raw amount" in {
    val res = matcher.matchOrders(
      taker.copy(_matchable = Some(OrderState(amountS = takerAmountS / 2, amountB = takerAmountB / 2, amountFee = takerFee / 2))),
      maker.copy(_matchable = Some(OrderState(amountS = makerAmountS / 2, amountB = makerAmountB / 2, amountFee = makerFee / 2)))
    )
    val expectRing = OrderRing(
      taker = ExpectedFill(
        order = taker.copy(_matchable = Some(OrderState())),
        pending = OrderState(amountS = takerAmountS / 2, amountB = takerAmountB / 2, amountFee = takerFee / 2),
        amountMargin = BigInt(0)
      ),
      maker = ExpectedFill(
        order = maker.copy(_matchable = Some(OrderState())),
        pending = OrderState(amountS = makerAmountS / 2, amountB = makerAmountB / 2, amountFee = makerFee / 2),
        amountMargin = BigInt(0)
      )
    )
    res.right.toOption should be(Some(expectRing))
  }

  "price3 just match" should "part fill if both matchables are a third of raw amount" in {
    //taker.amountB的计算通过amountS，因为精度问题，所以taker.amountB会在amountS的精度后为0
    val res = matcher.matchOrders(
      taker.copy(_matchable = Some(OrderState(
        amountS = BigInt("333333333333333333333333333"),
        amountB = BigInt("3333333333333333333333333330"),
        amountFee = BigInt("333333333333333333")
      ))),
      maker.copy(_matchable = Some(OrderState(
        amountS = BigInt("3333333333333333333333333333"),
        amountB = BigInt("333333333333333333333333333"),
        amountFee = BigInt("333333333333333333")
      )))
    )
    val expectRing = OrderRing(
      taker = ExpectedFill(
        order = taker.copy(_matchable = Some(OrderState())),
        pending = OrderState(
          amountS = BigInt("333333333333333333333333333"),
          amountB = BigInt("3333333333333333333333333330"),
          amountFee = BigInt("333333333333333333")
        ),
        amountMargin = BigInt(0)
      ),
      maker = ExpectedFill(
        order = maker.copy(_matchable = Some(OrderState(amountS = 3, amountFee = 1))),
        pending = OrderState(
          amountS = BigInt("3333333333333333333333333330"),
          amountB = BigInt("333333333333333333333333333"),
          amountFee = BigInt("333333333333333332")
        ),
        amountMargin = BigInt(0)
      )
    )
    res.right.toOption should be(Some(expectRing))
  }

  "price just match" should "part fill if one matchable is half of raw amount" in {
    val res = matcher.matchOrders(
      taker.copy(_matchable = Some(OrderState(amountS = takerAmountS / 2, amountB = takerAmountB / 2, amountFee = takerFee / 2))),
      maker.copy(_matchable = Some(OrderState(amountS = makerAmountS, amountB = makerAmountB, amountFee = makerFee)))
    )

    val expectRing = OrderRing(
      taker = ExpectedFill(
        order = taker.copy(_matchable = Some(OrderState())),
        pending = OrderState(amountS = takerAmountS / 2, amountB = takerAmountB / 2, amountFee = takerFee / 2),
        amountMargin = BigInt(0)
      ),
      maker = ExpectedFill(
        order = maker.copy(_matchable = Some(OrderState(
          amountS = makerAmountS - takerAmountB / 2,
          amountB = makerAmountB - takerAmountS / 2,
          amountFee = Rational(makerFee) - Rational(makerFee * (makerAmountS - takerAmountB / 2), makerAmountS)
        ))),
        pending = OrderState(
          amountS =
            takerAmountB / 2,
          amountB = takerAmountS / 2,
          amountFee = Rational(makerFee * (makerAmountS - takerAmountB / 2), makerAmountS)
        ),
        amountMargin = BigInt(0)
      )
    )
    res.right.toOption should be(Some(expectRing))
  }

  "price2 just match" should "fill with different decimal and raw amount isn't divisible by matchable " in {
    val otherDecimal = decimal.substring(0, 8)
    info(decimal)
    info(otherDecimal)
    val (takerAmountS, takerAmountB, takerFee) = (BigInt("11" + otherDecimal), BigInt("110" + decimal), BigInt("10" + decimal))
    val (makerAmountS, makerAmountB, makerFee) = (BigInt("110" + decimal), BigInt("11" + otherDecimal), BigInt("10" + decimal))
    val maker = sellDAI(makerAmountS, makerAmountB, makerFee)
    val taker = buyDAI(takerAmountS, takerAmountB, takerFee)
    val res = matcher.matchOrders(
      taker.copy(_matchable = Some(OrderState(
        amountS = BigInt("333333333"),
        amountB = BigInt("33333333300000000000"),
        amountFee = BigInt("3030303027272727272")
      ))),
      maker.copy(_matchable = Some(OrderState(
        amountS = BigInt("33333333333333333333"),
        amountB = BigInt("333333333"),
        amountFee = BigInt("3030303030303030303")
      )))
    )
    val expectRing = OrderRing(
      taker = ExpectedFill(
        order = taker.copy(_matchable = Some(OrderState())),
        pending = OrderState(
          amountS = BigInt("333333333"),
          amountB = BigInt("33333333300000000000"),
          amountFee = BigInt("3030303027272727272")
        ),
        amountMargin = BigInt(0)
      ),
      maker = ExpectedFill(
        order = maker.copy(_matchable = Some(OrderState(amountS = BigInt("33333333333"), amountFee = BigInt("3030303031")))),
        pending = OrderState(
          amountS = BigInt("33333333300000000000"),
          amountB = BigInt("333333333"),
          amountFee = BigInt("3030303027272727272")
        ),
        amountMargin = BigInt(0)
      )
    )
    res.right.toOption should be(Some(expectRing))
  }

  "price1 with margin" should " fill if matchable is raw amount" in {
    val (makerAmountS, makerAmountB, makerFee) = (
      BigInt("1500000000000" + decimal),
      BigInt("90000000000" + decimal),
      BigInt("90" + decimal)
    )
    val (takerAmountS, takerAmountB, takerFee) = (
      BigInt("10000000000" + decimal),
      BigInt("100000000000" + decimal),
      BigInt("10" + decimal)
    )
    val maker = sellDAI(makerAmountS, makerAmountB, makerFee)
    val taker = buyDAI(takerAmountS, takerAmountB, takerFee)

    val res = matcher.matchOrders(
      taker.copy(_matchable = Some(OrderState(amountS = takerAmountS / 2, amountB = takerAmountB / 2, amountFee = takerFee / 2))),
      maker.copy(_matchable = Some(OrderState(amountS = makerAmountS / 2, amountB = makerAmountB / 2, amountFee = makerFee / 2)))
    )
    val expectRing = OrderRing(
      taker = ExpectedFill(
        order = taker.copy(_matchable = Some(OrderState())),
        pending = OrderState(amountS = takerAmountS / 2, amountB = takerAmountB / 2, amountFee = takerFee / 2),
        amountMargin = BigInt("2000000000" + decimal)
      ),
      maker = ExpectedFill(
        order = maker.copy(_matchable = Some(OrderState(
          amountS = BigInt("700000000000" + decimal),
          amountB = BigInt("42000000000" + decimal),
          amountFee = BigInt("42" + decimal)
        ))),
        pending = OrderState(
          amountS = BigInt("50000000000" + decimal),
          amountB = BigInt("3000000000" + decimal),
          amountFee = BigInt("3" + decimal)
        ),
        amountMargin = BigInt(0)
      )
    )
    res.right.toOption should be(Some(expectRing))
  }

  "price with margin" should " fill if matchable is raw amount" in {
    val (makerAmountS, makerAmountB, makerFee) = (
      BigInt("1500000000000" + decimal),
      BigInt("90000000000" + decimal),
      BigInt("90" + decimal)
    )
    val (takerAmountS, takerAmountB, takerFee) = (
      BigInt("10000000000" + decimal),
      BigInt("100000000000" + decimal),
      BigInt("10" + decimal)
    )
    val maker = sellDAI(makerAmountS, makerAmountB, makerFee)
    val taker = buyDAI(takerAmountS, takerAmountB, takerFee)

    val res = matcher.matchOrders(
      taker.copy(_matchable = Some(OrderState(amountS = takerAmountS, amountB = takerAmountB, amountFee = takerFee))),
      maker.copy(_matchable = Some(OrderState(amountS = makerAmountS, amountB = makerAmountB, amountFee = makerFee)))
    )
    val expectRing = OrderRing(
      taker = ExpectedFill(
        order = taker.copy(_matchable = Some(OrderState())),
        pending = OrderState(amountS = takerAmountS, amountB = takerAmountB, amountFee = takerFee),
        amountMargin = BigInt("4000000000" + decimal)
      ),
      maker = ExpectedFill(
        order = maker.copy(_matchable = Some(OrderState(
          amountS = BigInt("1400000000000" + decimal),
          amountB = BigInt("84000000000" + decimal),
          amountFee = BigInt("84" + decimal)
        ))),
        pending = OrderState(
          amountS = BigInt("100000000000" + decimal),
          amountB = BigInt("6000000000" + decimal),
          amountFee = BigInt("6" + decimal)
        ),
        amountMargin = BigInt(0)
      )
    )
    res.right.toOption should be(Some(expectRing))

  }
}
