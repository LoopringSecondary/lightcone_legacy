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

case class MatchableState(
    amountS: BigInt = 0,
    amountB: BigInt = 0,
    amountFee: BigInt = 0) {

  def scaleBy(ratio: Rational) =
    MatchableState(
      (Rational(amountS) * ratio).toBigInt,
      (Rational(amountB) * ratio).toBigInt,
      (Rational(amountFee) * ratio).toBigInt
    )
}

// 注意!!!! 收益不能保证时,合约等比例计算,分母中不包含amountB
case class Matchable(
    id: String,
    tokenS: String,
    tokenB: String,
    tokenFee: String,
    amountS: BigInt = 0,
    amountB: BigInt = 0,
    amountFee: BigInt = 0,
    validSince: Long = -1,
    submittedAt: Long = -1,
    numAttempts: Int = 0,
    status: OrderStatus = OrderStatus.STATUS_NEW,
    walletSplitPercentage: Double = 0,
    _outstanding: Option[MatchableState] = None,
    _reserved: Option[MatchableState] = None,
    _actual: Option[MatchableState] = None,
    _matchable: Option[MatchableState] = None) {

  import ErrorCode._

  lazy val original = MatchableState(amountS, amountB, amountFee)

  def outstanding = _outstanding.getOrElse(original)
  def reserved = _reserved.getOrElse(MatchableState())
  def actual = _actual.getOrElse(MatchableState())
  def matchable = _matchable.getOrElse(MatchableState())

  // rate is the price of this sell-order
  lazy val rate = Rational(amountB, amountS)

  def withOutstandingAmountS(outstandingAmountS: BigInt) = {
    val r = Rational(outstandingAmountS, amountS)
    copy(_outstanding = Some(original.scaleBy(r)))
  }

  def withFilledAmountS(filledAmountS: BigInt) =
    withOutstandingAmountS((amountS - filledAmountS).max(0))

  // Advance methods with implicit contextual arguments
  private[core] def requestedAmount(implicit token: String): BigInt = {
    if (token == tokenS) {
      if (token == tokenFee) outstanding.amountS + outstanding.amountFee
      else outstanding.amountS
    } else if (token == tokenFee) {
      if (token != tokenB) outstanding.amountFee
      else (outstanding.amountFee - outstanding.amountB).max(0)
    } else {
      throw new IllegalStateException("requestedAmount")
    }
  }

  private[core] def reservedAmount()(implicit token: String) =
    throw new UnsupportedOperationException

  // 注意: v < requestBigInt
  private[core] def withReservedAmount(v: BigInt)(implicit token: String) = {
    val newReseved = {
      if (token == tokenS) {
        if (token == tokenFee) {
          val r = Rational(amountS, amountFee + amountS)
          val reservedAmountS = (Rational(v) * r).toBigInt
          MatchableState(reservedAmountS, 0, v - reservedAmountS)
        } else {
          MatchableState(v, 0, reserved.amountFee)
        }
      } else if (token == tokenFee) {
        MatchableState(reserved.amountS, 0, v)
      } else {
        throw new IllegalStateException("withReservedAmount")
      }
    }

    copy(_reserved = Some(newReseved)).updateActual()
  }

  private def updateActual() = {
    val r =
      if (amountFee <= 0)
        Rational(reserved.amountS, amountS)
      else if (tokenFee == tokenB) {
        if (amountFee <= amountB) Rational(reserved.amountS, amountS)
        else
          Rational(reserved.amountS, amountS)
            .min(Rational(reserved.amountFee, amountFee - amountB))
      } else {
        Rational(reserved.amountS, amountS)
          .min(Rational(reserved.amountFee, amountFee))
      }

    copy(_actual = Some(original.scaleBy(r)))
  }

  // Private methods
  private[core] def as(status: OrderStatus) = {
    copy(status = status).clearStates
  }

  private[core] def clearStates() =
    copy(_reserved = None, _actual = None, _matchable = None)

  private[core] def isSell()(implicit marketPair: MarketPair) =
    (tokenS == marketPair.baseToken)

  // for LRC-WETH market, this returns the number of WETH divided by the number of LRC
  private[core] def price(
    )(
      implicit
      marketPair: MarketPair,
      metadataManager: MetadataManager
    ) = {
    originalQuoteAmount / originalBaseAmount
  }

  // for LRC-WETH market, this returns the number of LRC
  private[core] def originalBaseAmount(
    )(
      implicit
      marketPair: MarketPair,
      metadataManager: MetadataManager
    ) = {
    if (tokenS == marketPair.quoteToken) fromWei(tokenB, original.amountB)
    else fromWei(tokenS, original.amountS)
  }

  // for LRC-WETH market, this returns the number of WETH
  private[core] def originalQuoteAmount(
    )(
      implicit
      marketPair: MarketPair,
      metadataManager: MetadataManager
    ) = {
    if (tokenS == marketPair.quoteToken) fromWei(tokenS, original.amountS)
    else fromWei(tokenB, original.amountB)
  }

  // for LRC-WETH market, this returns the number of LRC
  private[core] def matchableBaseAmount(
    )(
      implicit
      marketPair: MarketPair,
      metadataManager: MetadataManager
    ) = {
    if (tokenS == marketPair.quoteToken) fromWei(tokenB, matchable.amountB)
    else fromWei(tokenS, matchable.amountS)
  }

  // for LRC-WETH market, this returns the number of WETH
  private[core] def matchableQuoteAmount(
    )(
      implicit
      marketPair: MarketPair,
      metadataManager: MetadataManager
    ) = {
    if (tokenS == marketPair.quoteToken) fromWei(tokenS, matchable.amountS)
    else fromWei(tokenB, matchable.amountB)
  }

  private def fromWei(
      tokenAddr: String,
      amount: BigInt
    )(
      implicit
      metadataManager: MetadataManager
    ) = {
    metadataManager
      .getTokenWithAddress(tokenAddr)
      .map(_.fromWei(amount))
      .getOrElse {
        throw ErrorException(
          ERR_MATCHING_TOKEN_METADATA_UNAVAILABLE,
          s"token not found for $tokenAddr"
        )
      }
  }

}
