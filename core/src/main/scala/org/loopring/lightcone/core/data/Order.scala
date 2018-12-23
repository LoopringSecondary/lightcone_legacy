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
import org.loopring.lightcone.proto.XErrorCode._
import XOrderStatus._

case class OrderState(
    amountS: BigInt = 0,
    amountB: BigInt = 0,
    amountFee: BigInt = 0) {

  def scaleBy(ratio: Rational) =
    OrderState(
      (Rational(amountS) * ratio).bigintValue,
      (Rational(amountB) * ratio).bigintValue,
      (Rational(amountFee) * ratio).bigintValue
    )
}

// 注意!!!! 收益不能保证时,合约等比例计算,分母中不包含amountB
case class Order(
    id: String,
    tokenS: String,
    tokenB: String,
    tokenFee: String,
    amountS: BigInt = 0,
    amountB: BigInt = 0,
    amountFee: BigInt = 0,
    createdAt: Long = -1,
    updatedAt: Long = -1,
    status: XOrderStatus = STATUS_NEW,
    walletSplitPercentage: Double = 0,
    _outstanding: Option[OrderState] = None,
    _reserved: Option[OrderState] = None,
    _actual: Option[OrderState] = None,
    _matchable: Option[OrderState] = None) {

  lazy val original = OrderState(amountS, amountB, amountFee)

  def outstanding = _outstanding.getOrElse(original)
  def reserved = _reserved.getOrElse(OrderState())
  def actual = _actual.getOrElse(OrderState())
  def matchable = _matchable.getOrElse(OrderState())

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
        _reserved = Some(OrderState(reservedAmountS, 0, v - reservedAmountS))
      ).updateActual()
    } else if (token == tokenS && tokenFee != tokenS) {
      copy(_reserved = Some(OrderState(v, 0, reserved.amountFee)))
        .updateActual()
    } else if (token != tokenS && tokenFee == tokenB) {
      copy(_reserved = Some(OrderState(reserved.amountS, 0, v))).updateActual()
    } else {
      copy(_reserved = Some(OrderState(reserved.amountS, 0, v))).updateActual()
    }

  // Private methods
  private[core] def as(status: XOrderStatus) = {
    assert(status != STATUS_PENDING)
    copy(status = status, _reserved = None, _actual = None, _matchable = None)
  }

  private[core] def resetMatchable() = copy(_matchable = None)

  private[core] def amountSU()(implicit tokenManager: TokenManager) =
    fromWei(tokenS, actual.amountS)

  private[core] def amountBU()(implicit tokenManager: TokenManager) =
    fromWei(tokenB, actual.amountB)

  private[core] def amountFeeU()(implicit tokenManager: TokenManager) =
    fromWei(tokenFee, actual.amountFee)

  private[core] def matchableAmountSU()(implicit tokenManager: TokenManager) =
    fromWei(tokenS, matchable.amountS)

  private[core] def matchableAmountBU()(implicit tokenManager: TokenManager) =
    fromWei(tokenB, matchable.amountB)

  private[core] def matchableAmountFeeU()(implicit tokenManager: TokenManager) =
    fromWei(tokenFee, matchable.amountFee)

  private[core] def isSell()(implicit marketId: XMarketId) =
    (tokenS == marketId.secondary)

  private[core] def priceU(
    )(
      implicit marketId: XMarketId,
      tokenManager: TokenManager
    ) = {
    amountU / totalU
  }

  private[core] def amountU(
    )(
      implicit marketId: XMarketId,
      tokenManager: TokenManager
    ) = {
    if (tokenS == marketId.secondary) amountSU
    else amountBU
  }

  private[core] def totalU(
    )(
      implicit marketId: XMarketId,
      tokenManager: TokenManager
    ) = {
    if (tokenS == marketId.secondary) amountBU
    else amountSU
  }

  private[core] def matchableAmountU(
    )(
      implicit marketId: XMarketId,
      tokenManager: TokenManager
    ) = {
    if (tokenS == marketId.secondary) matchableAmountSU
    else matchableAmountBU
  }

  private[core] def matchableTotalU(
    )(
      implicit marketId: XMarketId,
      tokenManager: TokenManager
    ) = {
    if (tokenS == marketId.secondary) matchableAmountBU
    else matchableAmountSU
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
      implicit tokenManager: TokenManager
    ) = {
    if (!tokenManager.hasToken(tokenAddr)) {

      throw ErrorException(
        ERR_MATCHING_TOKEN_METADATA_UNAVAILABLE,
        s"no metadata available for token $tokenAddr"
      )
    }
    val token = tokenManager.getToken(tokenAddr)
    token.fromWei(amount)
  }
}
