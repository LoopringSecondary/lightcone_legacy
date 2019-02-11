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

package io.lightcone.core.testing

import io.lightcone.core._

trait OrderHelper extends Object with Constants {

  val rand = new scala.util.Random(31)

  case class AmountToken(
      amount: Double,
      tokenAddress: String) {
    def -->(another: AmountToken) = OrderRep(this, another, None)
    def <--(another: AmountToken) = OrderRep(another, this, None)
  }

  case class OrderRep(
      sell: AmountToken,
      buy: AmountToken,
      fee: Option[AmountToken]) {
    def --(fee: AmountToken) = copy(fee = Some(fee))
  }

  implicit class Rich_DoubleAmount(v: Double) {
    def ^(str: String) = AmountToken(v, str)
    def lrc = AmountToken(v, LRC)
    def gto = AmountToken(v, GTO)
    def weth = AmountToken(v, WETH)
    def dai = AmountToken(v, DAI)
  }

  implicit class Rich_StringAddress(owner: String) {

    // TODO(hongyu): need to make sure signature are all calculated. this is mostly
    // for integration testing.
    def |>>>(or: OrderRep): RawOrder =
      RawOrder(
        owner = owner,
        tokenS = or.sell.tokenAddress,
        tokenB = or.buy.tokenAddress
      )

    // TODO(dongw): need to convert this to Matchable but user MetadataManager to put
    // trailing 0s to amountS, amountB, and amountFee, in other world, convert to wei.
    def |>>(or: OrderRep): Matchable =
      Matchable(
        id = rand.nextString(20),
        tokenS = or.sell.tokenAddress,
        tokenB = or.buy.tokenAddress,
        tokenFee = or.fee.getOrElse(or.sell).tokenAddress,
        amountS = BigInt(or.sell.amount.toLong),
        amountB = BigInt(or.buy.amount.toLong),
        amountFee = BigInt(or.fee.getOrElse(or.sell).amount.toLong),
        validSince = 0,
        submittedAt = 0,
        numAttempts = 0,
        status = OrderStatus.STATUS_NEW,
        walletSplitPercentage = 20
      )

    def |>(or: OrderRep): Matchable =
      Matchable(
        id = rand.nextString(20),
        tokenS = or.sell.tokenAddress,
        tokenB = or.buy.tokenAddress,
        tokenFee = or.fee.getOrElse(or.sell).tokenAddress,
        amountS = BigInt(or.sell.amount.toLong),
        amountB = BigInt(or.buy.amount.toLong),
        amountFee = BigInt(or.fee.getOrElse(or.sell).amount.toLong),
        validSince = 0,
        submittedAt = 0,
        numAttempts = 0,
        status = OrderStatus.STATUS_NEW,
        walletSplitPercentage = 20
      )
  }
}
