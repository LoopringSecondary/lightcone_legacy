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

object TokenAmount {
  def zero(tokenSymbol: String) = new TokenAmount(BigInt(0), tokenSymbol)
}

case class TokenAmount(
    value: BigInt,
    tokenSymbol: String) {

  def scaledBy(ratio: Rational) =
    copy(value = (Rational(value) * ratio).bigintValue)

  def zero = copy(value = BigInt(0))

  def /(that: TokenAmount) = Rational(value, that.value)

  def -(that: TokenAmount) = {
    assert(tokenSymbol == that.tokenSymbol)
    copy(value = value - that.value)
  }

  def +(that: TokenAmount) = {
    assert(tokenSymbol == that.tokenSymbol)
    copy(value = value + that.value)
  }

  def >(that: TokenAmount): Boolean = {
    assert(tokenSymbol == that.tokenSymbol)
    value > that.value
  }

  def =~(that: TokenAmount): Boolean = tokenSymbol == that.tokenSymbol
  def =~(token: String): Boolean = tokenSymbol == token

  def !~(that: TokenAmount): Boolean = tokenSymbol != that.tokenSymbol
  def !~(token: String): Boolean = tokenSymbol != token

  def max(m: BigInt) = if (value < m) copy(value = m) else this
  def min(m: BigInt) = if (value > m) copy(value = m) else this
  def nonZero() = value > 0
  def isZero() = value <= 0

  def displayableValue(
      implicit tokenMetadataManager: TokenMetadataManager
    ): Double = {
    if (!tokenMetadataManager.hasTokenBySymbol(tokenSymbol)) {
      throw ErrorException(
        ERR_MATCHING_TOKEN_METADATA_UNAVAILABLE,
        s"no metadata available for token: $tokenSymbol"
      )
    }
    val metadata = tokenMetadataManager.getTokenByAddress(tokenSymbol).get
    val decimals = metadata.decimals
    Rational(value, BigInt(10).pow(decimals)).doubleValue
  }
}

case class OrderState2(
    amountS: TokenAmount,
    amountB: TokenAmount,
    amountF: TokenAmount) {

  def scaledBy(ratio: Rational) =
    OrderState2(
      amountS.scaledBy(ratio),
      amountB.scaledBy(ratio),
      amountF.scaledBy(ratio)
    )
}

case class OrderState(
    amountS: BigInt = 0,
    amountB: BigInt = 0,
    amountFee: BigInt = 0) {

  def scaledBy(ratio: Rational) =
    OrderState(
      (Rational(amountS) * ratio).bigintValue,
      (Rational(amountB) * ratio).bigintValue,
      (Rational(amountFee) * ratio).bigintValue
    )
}

// 注意!!!! 收益不能保证时,合约等比例计算,分母中不包含amountB

case class Order2(
    id: String,
    amountS: TokenAmount,
    amountB: TokenAmount,
    amountF: TokenAmount,
    createdAt: Long = 0,
    updatedAt: Long = 0,
    status: XOrderStatus = STATUS_NEW,
    walletSplitPercentage: Double = 0,
    _outstanding: Option[OrderState2] = None,
    _reserved: Option[OrderState2] = None,
    _actual: Option[OrderState2] = None,
    _matchable: Option[OrderState2] = None) {

  lazy val original = OrderState2(amountS, amountB, amountF)

  def isSell(implicit marketId: XMarketId) =
    (amountS.tokenSymbol == marketId.secondary)

  def zeroState = OrderState2(amountS.zero, amountB.zero, amountF.zero)

  def outstanding = _outstanding.getOrElse(original)
  def reserved = _reserved.getOrElse(zeroState)
  def actual = _actual.getOrElse(zeroState)
  def matchable = _matchable.getOrElse(zeroState)

  def rate = Rational(amountB.value, amountS.value)

  def withOutstandingAmountS(outstandingAmountS: TokenAmount) = {
    val r = outstandingAmountS / amountS
    copy(_outstanding = Some(original.scaledBy(r)))
  }

  def withFilledAmountS(filledAmountS: TokenAmount) =
    withOutstandingAmountS((amountS - filledAmountS).max(0))

  // Private methods
  private[core] def as(status: XOrderStatus) = {
    assert(status != STATUS_PENDING)
    copy(status = status, _reserved = None, _actual = None, _matchable = None)
  }

  private[core] def resetMatchable() = copy(_matchable = None)

  // methods to convert to displayable values
  private[core] def displayablePrice(
      implicit marketId: XMarketId,
      tokenMetadataManager: TokenMetadataManager
    ) = {
    if (amountS.tokenSymbol == marketId.secondary)
      (amountS / amountB).doubleValue
    else (amountB / amountS).doubleValue
  }

  private[core] def displayableAmount(
    )(
      implicit marketId: XMarketId,
      tokenMetadataManager: TokenMetadataManager
    ) = {
    if (amountS.tokenSymbol == marketId.secondary)
      amountS.displayableValue
    else
      amountB.displayableValue
  }

  private[core] def displayableTotal(
    )(
      implicit marketId: XMarketId,
      tokenMetadataManager: TokenMetadataManager
    ) = {
    if (amountS.tokenSymbol == marketId.secondary)
      amountB.displayableValue
    else
      amountS.displayableValue
  }

  private[core] def requestedAmount()(implicit token: String): TokenAmount =
    if (amountS =~ token && amountF =~ amountS) {
      outstanding.amountS + outstanding.amountF
    } else if (amountS =~ token && amountF !~ amountS) {
      outstanding.amountS
    } else if (amountS !~ token && amountF =~ amountB) {
      if (outstanding.amountF > outstanding.amountB)
        outstanding.amountF - outstanding.amountB
      else TokenAmount.zero(token)
    } else {
      outstanding.amountF
    }

  private[core] def reservedAmount()(implicit token: String): TokenAmount =
    if (amountS =~ token && amountF =~ amountS) {
      reserved.amountS + reserved.amountF
    } else if (amountS =~ token && amountF !~ amountS) {
      reserved.amountS
    } else if (amountS !~ token && amountB =~ amountF) {
      reserved.amountB + reserved.amountF
    } else {
      reserved.amountF
    }

  private[core] def withReservedAmount(
      v: TokenAmount
    )(
      implicit token: String
    ) = {
    if (amountS =~ token && amountF =~ token) {
      val r = amountS / (amountF + amountS)

      val reservedAmountS = v.scaledBy(r) //(Rational(v) * r).bigintValue()
      copy(
        _reserved =
          Some(OrderState2(reservedAmountS, amountB.zero, v - reservedAmountS))
      ).updateActual()
    } else if (amountS =~ token && amountF !~ token) {
      copy(_reserved = Some(OrderState2(v, amountB.zero, reserved.amountF)))
        .updateActual()
    } else if (amountS !~ token && amountF =~ amountB) {
      copy(_reserved = Some(OrderState2(reserved.amountS, amountB.zero, v)))
        .updateActual()
    } else {
      copy(_reserved = Some(OrderState2(reserved.amountS, amountB.zero, v)))
        .updateActual()
    }
  }

  private def updateActual() = {
    var r = reserved.amountS / amountS
    if (amountF.nonZero) {
      if (amountF =~ amountB && reserved.amountF.nonZero) {
        r = (reserved.amountF / (amountF - amountB)).min(r)
      } else if (amountF =~ amountB && reserved.amountF.isZero) {
        // r = r
      } else {
        r = (reserved.amountF / amountF).min(r)
      }
    }
    copy(_actual = Some(original.scaledBy(r)))
  }
}

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

  def original = OrderState(amountS, amountB, amountFee)

  def outstanding =
    _outstanding.getOrElse(OrderState(amountS, amountB, amountFee))
  def reserved = _reserved.getOrElse(OrderState())
  def actual = _actual.getOrElse(OrderState())
  def matchable = _matchable.getOrElse(OrderState())

  // rate is the price of this sell-order
  lazy val rate = Rational(amountB, amountS)

  def withOutstandingAmountS(outstandingAmountS: BigInt) = {
    val r = Rational(outstandingAmountS, amountS)
    copy(_outstanding = Some(original.scaledBy(r)))
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

  private[core] def displayableAmountS(
    )(
      implicit tokenMetadataManager: TokenMetadataManager
    ) =
    calcDisplayableAmount(tokenS, actual.amountS)

  private[core] def displayableAmountB(
    )(
      implicit tokenMetadataManager: TokenMetadataManager
    ) =
    calcDisplayableAmount(tokenB, actual.amountB)

  private[core] def displayableAmountFee(
    )(
      implicit tokenMetadataManager: TokenMetadataManager
    ) =
    calcDisplayableAmount(tokenFee, actual.amountFee)

  private[core] def isSell()(implicit marketId: XMarketId) =
    (tokenS == marketId.secondary)

  private[core] def displayablePrice(
    )(
      implicit marketId: XMarketId,
      tokenMetadataManager: TokenMetadataManager
    ) = {
    if (tokenS == marketId.secondary) Rational(amountS, amountB).doubleValue
    else Rational(amountB, amountS).doubleValue
  }

  private[core] def displayableAmount(
    )(
      implicit marketId: XMarketId,
      tokenMetadataManager: TokenMetadataManager
    ) = {
    if (tokenS == marketId.secondary) displayableAmountS
    else displayableAmountB
  }

  private[core] def displayableTotal(
    )(
      implicit marketId: XMarketId,
      tokenMetadataManager: TokenMetadataManager
    ) = {
    if (tokenS == marketId.secondary) displayableAmountB
    else displayableAmountS
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
    copy(_actual = Some(original.scaledBy(r)))
  }

  private def calcDisplayableAmount(
      token: String,
      amount: BigInt
    )(
      implicit tokenMetadataManager: TokenMetadataManager
    ) = {
    if (!tokenMetadataManager.hasTokenByAddress(token)) {
      throw ErrorException(
        ERR_MATCHING_TOKEN_METADATA_UNAVAILABLE,
        s"no metadata available for token $token"
      )
    }
    val metadata = tokenMetadataManager.getTokenByAddress(token).get
    val decimals = metadata.decimals
    (Rational(amount) / Rational(Math.pow(10, decimals))).doubleValue
  }
}
