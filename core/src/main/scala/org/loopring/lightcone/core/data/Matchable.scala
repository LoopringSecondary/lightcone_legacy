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

import org.loopring.lightcone.core.base._
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.ErrorCode._
import OrderStatus._

case class MatchableState(
    amountS: BigInt = 0,
    amountB: BigInt = 0,
    amountFee: BigInt = 0) {

  def scaleBy(ratio: Rational) =
    MatchableState(
      (Rational(amountS) * ratio).bigintValue,
      (Rational(amountB) * ratio).bigintValue,
      (Rational(amountFee) * ratio).bigintValue
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
    status: OrderStatus = STATUS_NEW,
    walletSplitPercentage: Double = 0,
    _outstanding: Option[MatchableState] = None,
    _reserved: Option[MatchableState] = None,
    _actual: Option[MatchableState] = None,
    _matchable: Option[MatchableState] = None) {

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
  private[core] def requestedAmount()(implicit token: String): BigInt =
    if (token == tokenS && tokenFee == tokenS) {
      outstanding.amountS + outstanding.amountFee
    } else if (token == tokenS && tokenFee != tokenS) {
      outstanding.amountS
    } else if (token != tokenS && tokenFee == tokenB) {
      if (outstanding.amountFee > outstanding.amountB)
        outstanding.amountFee - outstanding.amountB
      else 0
    } else {
      outstanding.amountFee
    }

  private[core] def reservedAmount()(implicit token: String) =
    if (token == tokenS && tokenFee == tokenS) {
      reserved.amountS + reserved.amountFee
    } else if (token == tokenS && tokenFee != tokenS) {
      reserved.amountS
    } else if (token != tokenS && tokenFee == tokenB) {
      reserved.amountB + reserved.amountFee
    } else {
      reserved.amountFee
    }

  // 注意: v < requestBigInt
  private[core] def withReservedAmount(v: BigInt)(implicit token: String) =
    if (token == tokenS && tokenFee == tokenS) {
      val r = Rational(amountS, amountFee + amountS)
      val reservedAmountS = (Rational(v) * r).bigintValue()
      copy(
        _reserved =
          Some(MatchableState(reservedAmountS, 0, v - reservedAmountS))
      ).updateActual()
    } else if (token == tokenS && tokenFee != tokenS) {
      copy(_reserved = Some(MatchableState(v, 0, reserved.amountFee)))
        .updateActual()
    } else if (token != tokenS && tokenFee == tokenB) {
      copy(_reserved = Some(MatchableState(reserved.amountS, 0, v)))
        .updateActual()
    } else {
      copy(_reserved = Some(MatchableState(reserved.amountS, 0, v)))
        .updateActual()
    }

  // Private methods
  private[core] def as(status: OrderStatus) = {
    assert(status != STATUS_PENDING)
    copy(status = status).clearStates
  }

  private[core] def clearStates() =
    copy(_reserved = None, _actual = None, _matchable = None)

  private[core] def isSell()(implicit marketId: MarketId) =
    (tokenS == marketId.secondary)

  private[core] def price(
    )(
      implicit
      marketId: MarketId,
      metadataManager: MetadataManager
    ) = {
    originalAmount / originalTotal
  }

  private[core] def originalAmount(
    )(
      implicit
      marketId: MarketId,
      metadataManager: MetadataManager
    ) = {
    if (tokenS == marketId.secondary) fromWei(tokenS, original.amountS)
    else fromWei(tokenB, original.amountB)
  }

  private[core] def originalTotal(
    )(
      implicit
      marketId: MarketId,
      metadataManager: MetadataManager
    ) = {
    if (tokenS == marketId.secondary) fromWei(tokenB, original.amountB)
    else fromWei(tokenS, original.amountS)
  }

  private[core] def matchableAmount(
    )(
      implicit
      marketId: MarketId,
      metadataManager: MetadataManager
    ) = {
    if (tokenS == marketId.secondary) fromWei(tokenS, matchable.amountS)
    else fromWei(tokenB, matchable.amountB)
  }

  private[core] def matchableTotal(
    )(
      implicit
      marketId: MarketId,
      metadataManager: MetadataManager
    ) = {
    if (tokenS == marketId.secondary) fromWei(tokenB, matchable.amountB)
    else fromWei(tokenS, matchable.amountS)
  }

  private def updateActual() = {
    var r = Rational(reserved.amountS, amountS)
    if (amountFee > 0) {
      if (tokenFee == tokenB && reserved.amountFee > 0) {
        r = r min Rational(reserved.amountFee, amountFee - amountB)
      } else if (tokenFee == tokenB && reserved.amountFee == 0) {
        // r = r
      } else {
        r = r min Rational(reserved.amountFee, amountFee)
      }
    }
    copy(_actual = Some(original.scaleBy(r)))
  }

  private def fromWei(
      tokenAddr: String,
      amount: BigInt
    )(
      implicit
      metadataManager: MetadataManager
    ) = {
    if (!metadataManager.hasToken(tokenAddr)) {

      throw ErrorException(
        ERR_MATCHING_TOKEN_METADATA_UNAVAILABLE,
        s"no metadata available for token $tokenAddr"
      )
    }
    val token = metadataManager
      .getToken(tokenAddr)
      .getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found token:$tokenAddr"
        )
      )
    token.fromWei(amount)
  }
}
