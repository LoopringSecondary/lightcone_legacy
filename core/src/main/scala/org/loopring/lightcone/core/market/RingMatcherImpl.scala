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

import org.loopring.lightcone.core.data._
import org.loopring.lightcone.proto._
import org.slf4s.Logging
import XErrorCode._

class RingMatcherImpl()(implicit rie: RingIncomeEstimator)
    extends RingMatcher
    with Logging {

  def matchOrders(
      taker: Order2,
      maker: Order2,
      minFiatValue: Double = 0
    ): Either[XErrorCode, OrderRing] = {
    val ringOpt = makeRing(maker, taker)
    ringOpt match {
      case Right(ring) if !rie.isProfitable(ring, minFiatValue) =>
        Left(ERR_MATCHING_INCOME_TOO_SMALL)
      case other => other
    }
  }

  private def makeRing(
      maker: Order2,
      taker: Order2
    ): Either[XErrorCode, OrderRing] = {
    if (taker.amountB.value <= 0 || taker.amountS.value <= 0) {
      Left(ERR_MATCHING_INVALID_TAKER_ORDER)
    } else if (maker.amountB.value <= 0 || maker.amountS.value <= 0) {
      Left(ERR_MATCHING_INVALID_MAKER_ORDER)
    } else if (taker.matchable.amountS.value <= 0 || taker.matchable.amountB.value <= 0) {
      Left(ERR_MATCHING_TAKER_COMPLETELY_FILLED)
    } else if (maker.matchable.amountS.value <= 0 || maker.matchable.amountB.value <= 0) {
      Left(ERR_MATCHING_MAKER_COMPLETELY_FILLED)
    } else if (maker.amountS * taker.amountS < maker.amountB * taker.amountB) {
      Left(ERR_MATCHING_ORDERS_NOT_TRADABLE)
    } else {
      /*合约逻辑：
    取小的成交量计算，按照订单顺序，如果下一单的卖需要缩减，则第一单为最小单
    与顺序相关
    因此生成订单时，按照maker,taker的顺序
       */
      //taker的卖出大于maker的买入时，taker需要缩减，则认为最小交易量为maker的卖出，否则为taker的买入

      val (makerVolume, takerVolume) =
        if (taker.matchable.amountS.value > maker.matchable.amountB.value) {
          (
            OrderState2(maker.matchable.amountS, maker.matchable.amountB),
            OrderState2(
              maker.matchable.amountB,
              maker.matchable.amountB * (taker.amountB / taker.amountS)
            )
          )
        } else {
          (
            OrderState2(
              taker.matchable.amountB,
              taker.matchable.amountB * (maker.amountB / maker.amountS)
            ),
            OrderState2(taker.matchable.amountS, taker.matchable.amountB)
          )
        }

      //fee 按照卖出的比例计算
      val makerF = maker.matchable.amountF *
        (makerVolume.amountS /
          maker.matchable.amountS)

      val takerF = taker.matchable.amountF *
        (takerVolume.amountS /
          taker.matchable.amountS)

      val makerMargin = makerVolume.amountS - takerVolume.amountB
      val takerMargin = takerVolume.amountS - makerVolume.amountB

      Right(
        OrderRing(
          maker = ExpectedFill(
            order = maker.copy(
              _matchable = Some(
                OrderState2(
                  maker.matchable.amountS - makerVolume.amountS,
                  maker.matchable.amountB - makerVolume.amountB,
                  maker.matchable.amountF - makerF
                )
              )
            ),
            pending = makerVolume.copy(amountF = makerF),
            amountMargin = makerMargin
          ),
          taker = ExpectedFill(
            order = taker.copy(
              _matchable = Some(
                OrderState2(
                  taker.matchable.amountS - takerVolume.amountS,
                  taker.matchable.amountB - takerVolume.amountB,
                  taker.matchable.amountF - takerF
                )
              )
            ),
            pending = takerVolume.copy(amountF = takerF),
            amountMargin = takerMargin
          )
        )
      )
    }
  }
}
