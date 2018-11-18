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

import org.web3j.crypto.Hash
import org.web3j.utils.Numeric

package object data {
  implicit class RichBigInt(this_ : BigInt) {
    def min(that: BigInt): BigInt = if (this_ < that) this_ else that
    def max(that: BigInt): BigInt = if (this_ > that) this_ else that
  }

  implicit def rational2BigInt(r: Rational) = r.bigintValue

  implicit class RichExpectedFill(raw: ExpectedFill) {
    def id = raw.order.id
  }

  implicit class RichOrderRing(
      raw: OrderRing
  ) {
    // Switching maker and taker should have the same id.
    def id(): String = {
      val hash = BigInt(Hash.sha3(raw.maker.id.getBytes)) ^
        BigInt(Hash.sha3(raw.taker.id.getBytes()))
      Numeric.toHexString(hash.toByteArray)
    }

    //中间价格，可以在显示深度价格时使用,简单的中间价
    //根据计价token来计算中间价格
    def middleRate(feeToken: String): Double = {
      val makerSellPrice = Rational(
        raw.maker.order.amountS,
        raw.maker.order.amountB
      ).doubleValue()

      val takerSellPrice = Rational(
        raw.taker.order.amountS,
        raw.taker.order.amountB
      ).doubleValue()

      val productPrice = takerSellPrice * makerSellPrice
      val rateOfPrice = math.pow(productPrice, 0.5)
      val priceByMaker = makerSellPrice * rateOfPrice

      if (raw.maker.order.tokenS == feeToken) priceByMaker
      else 1 / priceByMaker
    }

    def orders() = Seq(raw.maker.order, raw.taker.order)
  }
}
