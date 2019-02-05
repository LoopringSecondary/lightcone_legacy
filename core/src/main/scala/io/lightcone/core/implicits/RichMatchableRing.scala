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

private[core] class RichMatchableRing(ring: MatchableRing) {

  // Switching maker and taker should have the same id.
  def id(): String = {
    createRingIdByOrderHash(ring.maker.id, ring.taker.id)
  }

  //中间价格，可以在显示深度价格时使用,简单的中间价
  //根据计价token来计算中间价格
  def middleRate(feeToken: String): Double = {
    val makerSellPrice =
      Rational(ring.maker.order.amountS, ring.maker.order.amountB).doubleValue()

    val takerSellPrice =
      Rational(ring.taker.order.amountS, ring.taker.order.amountB).doubleValue()

    val productPrice = takerSellPrice * makerSellPrice
    val rateOfPrice = Math.pow(productPrice, 0.5)
    val priceByMaker = makerSellPrice * rateOfPrice

    if (ring.maker.order.tokenS == feeToken) priceByMaker
    else 1 / priceByMaker
  }

  def orders() = Seq(ring.maker.order, ring.taker.order)
}
