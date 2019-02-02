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

package org.loopring.lightcone.core.data

import org.web3j.utils.Numeric
import spire.math.Rational

case class ExpectedMatchableFill(
    order: Matchable,
    pending: MatchableState,
    amountMargin: BigInt = 0) {
  def id = order.id
}

object MatchableRing {

  def createRingIdWithOrderHashes(
      orderhash1: String,
      orderhash2: String
    ) = {
    val hash = Numeric.toBigInt(orderhash1) xor Numeric.toBigInt(orderhash2)
    Numeric.toHexString(hash.toByteArray).toLowerCase()
  }
}

case class MatchableRing(
    maker: ExpectedMatchableFill,
    taker: ExpectedMatchableFill) {

  import MatchableRing._

  // Switching maker and taker should have the same id.
  def id(): String = {
    createRingIdWithOrderHashes(maker.id, taker.id)
  }

  //中间价格，可以在显示深度价格时使用,简单的中间价
  //根据计价token来计算中间价格
  def middleRate(feeToken: String): Double = {
    val makerSellPrice =
      Rational(maker.order.amountS, maker.order.amountB).doubleValue()

    val takerSellPrice =
      Rational(taker.order.amountS, taker.order.amountB).doubleValue()

    val productPrice = takerSellPrice * makerSellPrice
    val rateOfPrice = Math.pow(productPrice, 0.5)
    val priceByMaker = makerSellPrice * rateOfPrice

    if (maker.order.tokenS == feeToken) priceByMaker
    else 1 / priceByMaker
  }

  def orders() = Seq(maker.order, taker.order)
}
