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

package org.loopring.lightcone.core

/// import org.loopring.lightcone.proto._

import com.google.inject.Inject
import spire.math.Rational

trait RingIncomeEvaluator {
  def getRingIncome(ring: MatchableRing): Double

  def isProfitable(
      ring: MatchableRing,
      fiatValueThreshold: Double
    ): Boolean
}

final class RingIncomeEvaluatorImpl @Inject()(
    implicit
    mm: MetadataManager,
    tve: TokenValueEvaluator)
    extends RingIncomeEvaluator {

  def getRingIncome(ring: MatchableRing) =
    getExpectedFillIncomeFiatValue(ring.maker) +
      getExpectedFillIncomeFiatValue(ring.taker)

  def isProfitable(
      ring: MatchableRing,
      fiatValueThreshold: Double
    ) =
    getRingIncome(ring) >= fiatValueThreshold

  private def getExpectedFillIncomeFiatValue(fill: ExpectedMatchableFill) = {

    val (order, pending, amountMargin) =
      (fill.order, fill.pending, fill.amountMargin)

    val rate = (1 - order.walletSplitPercentage) *
      (1 - mm.getBurnRate(order.tokenFee).forMarket)

    val fiatFee = rate * tve.getValue(order.tokenFee, pending.amountFee)

    // when we do not know the price of tokenS, try to use tokenB's price to calculate
    // the price.
    val fiatMargin =
      if (mm.hasToken(order.tokenS)) {
        tve.getValue(order.tokenS, amountMargin)
      } else {
        tve.getValue(
          order.tokenB,
          Rational(amountMargin * order.amountS, order.amountB)
        )
      }
    fiatFee + fiatMargin
  }
}
