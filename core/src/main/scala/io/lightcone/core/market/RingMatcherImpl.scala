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

import org.slf4s.Logging
import spire.math.{Rational => R}

class RingMatcherImpl()(implicit rie: RingIncomeEvaluator)
    extends RingMatcher
    with Logging {

  import ErrorCode._

  def matchOrders(
      taker: Matchable,
      maker: Matchable,
      minRequiredIncome: Double = 0
    ): Either[ErrorCode, MatchableRing] = {
    val ringOpt = makeRing(maker, taker)
    ringOpt match {
      case Right(ring) if !rie.isProfitable(ring, minRequiredIncome) =>
        Left(ERR_MATCHING_INCOME_TOO_SMALL)
      case other => other
    }
  }

  private def makeRing(
      maker: Matchable,
      taker: Matchable
    ): Either[ErrorCode, MatchableRing] = {
    log.debug(
      s"RingMatcherImpl -- makeRing -- maker:${maker.id}, ${maker.numAttempts}, taker: ${taker.id}, ${taker.numAttempts}"
    )
    if (taker.amountB <= 0 || taker.amountS <= 0) {
      Left(ERR_MATCHING_INVALID_TAKER_ORDER)
    } else if (maker.amountB <= 0 || maker.amountS <= 0) {
      Left(ERR_MATCHING_INVALID_MAKER_ORDER)
    } else if (taker.matchable.amountS <= 0 || taker.matchable.amountB <= 0) {
      Left(ERR_MATCHING_TAKER_COMPLETELY_FILLED)
    } else if (maker.matchable.amountS <= 0 || maker.matchable.amountB <= 0) {
      Left(ERR_MATCHING_MAKER_COMPLETELY_FILLED)
    } else if (maker.amountS * taker.amountS < maker.amountB * taker.amountB) {
      Left(ERR_MATCHING_ORDERS_NOT_TRADABLE)
    } else {

      // 取小的成交量计算，按照订单顺序，如果下一单的卖需要缩减，则第一单为最小单
      // 与顺序相关
      // 因此生成订单时，按照maker,taker的顺序
      // taker的卖出大于maker的买入时，taker需要缩减，则认为最小交易量为maker的卖出，否则为taker的买入

      val ts = taker.amountS
      val tb = taker.amountB
      val tms = taker.matchable.amountS
      val tmb = taker.matchable.amountB
      val tmf = taker.matchable.amountFee

      val ms = maker.amountS
      val mb = maker.amountB
      val mms = maker.matchable.amountS
      val mmb = maker.matchable.amountB
      val mmf = maker.matchable.amountFee

      val makerVolume =
        if (tms > mmb) MatchableState(mms, mmb)
        else MatchableState(tmb, (R(tmb) * R(mb, ms)).toBigInt)

      val takerVolume =
        if (tms > mmb) MatchableState(mmb, (R(mmb) * R(tb, ts)).toBigInt)
        else MatchableState(tms, tmb)

      //fee 按照卖出的比例计算
      val makerFee = mmf * makerVolume.amountS / mms
      val takerFee = tmf * takerVolume.amountS / tms

      val makerMargin = makerVolume.amountS - takerVolume.amountB
      val takerMargin = takerVolume.amountS - makerVolume.amountB

      val maker_ = ExpectedMatchableFill(
        maker.copy(
          _matchable = Some(
            MatchableState(
              mms - makerVolume.amountS,
              mmb - makerVolume.amountB,
              mmf - makerFee
            )
          )
        ),
        makerVolume.copy(amountFee = makerFee),
        makerMargin
      )

      val taker_ = ExpectedMatchableFill(
        taker.copy(
          _matchable = Some(
            MatchableState(
              tms - takerVolume.amountS,
              tmb - takerVolume.amountB,
              tmf - takerFee
            )
          )
        ),
        takerVolume.copy(amountFee = takerFee),
        takerMargin
      )

      Right(MatchableRing(maker_, taker_))
    }
  }
}
