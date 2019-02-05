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

package org.loopring.lightcone

import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import spire.math.Rational

package object core {
  implicit class RichOrderbookSlot(this_ : Orderbook.Slot) {

    def +(that: Orderbook.Slot) = {
      assert(this_.slot == that.slot)
      Orderbook.Slot(
        this_.slot,
        this_.amount + that.amount,
        this_.total + that.total
      )
    }

    def -(that: Orderbook.Slot) = {
      assert(this_.slot == that.slot)
      Orderbook.Slot(
        this_.slot,
        this_.amount - that.amount,
        this_.total - that.total
      )
    }
  }

  implicit class RichBigInt(this_ : BigInt) {
    def min(that: BigInt): BigInt = if (this_ < that) this_ else that
    def max(that: BigInt): BigInt = if (this_ > that) this_ else that
  }

  implicit def rational2BigInt(r: Rational) = r.toBigInt

  implicit class RichExpectedFill(raw: ExpectedMatchableFill) {
    def id = raw.order.id
  }

  def createRingIdByOrderHash(
      orderhash1: String,
      orderhash2: String
    ) = {
    val hash = Numeric.toBigInt(orderhash1) xor
      Numeric.toBigInt(orderhash2)
    Numeric.toHexString(hash.toByteArray).toLowerCase()
  }

  implicit class RichOrderRing(raw: MatchableRing) {

    // Switching maker and taker should have the same id.
    def id(): String = {
      createRingIdByOrderHash(raw.maker.id, raw.taker.id)
    }

    //中间价格，可以在显示深度价格时使用,简单的中间价
    //根据计价token来计算中间价格
    def middleRate(feeToken: String): Double = {
      val makerSellPrice =
        Rational(raw.maker.order.amountS, raw.maker.order.amountB).doubleValue()

      val takerSellPrice =
        Rational(raw.taker.order.amountS, raw.taker.order.amountB).doubleValue()

      val productPrice = takerSellPrice * makerSellPrice
      val rateOfPrice = Math.pow(productPrice, 0.5)
      val priceByMaker = makerSellPrice * rateOfPrice

      if (raw.maker.order.tokenS == feeToken) priceByMaker
      else 1 / priceByMaker
    }

    def orders() = Seq(raw.maker.order, raw.taker.order)
  }
}
