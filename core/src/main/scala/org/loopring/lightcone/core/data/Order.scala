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

import XOrderStatus._

case class OrderState(
    amountS: BigInt = 0,
    amountB: BigInt = 0,
    amountFee: BigInt = 0
) {
  def scaleBy(ratio: Rational) = OrderState(
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
    status: XOrderStatus = NEW,
    walletSplitPercentage: Double = 0,
    _outstanding: Option[OrderState] = None,
    _reserved: Option[OrderState] = None,
    _actual: Option[OrderState] = None,
    _matchable: Option[OrderState] = None
) {

  def original = OrderState(amountS, amountB, amountFee)
  def outstanding = _outstanding.getOrElse(OrderState(amountS, amountB, amountFee))
  def reserved = _reserved.getOrElse(OrderState())
  def actual = _actual.getOrElse(OrderState())
  def matchable = _matchable.getOrElse(OrderState())

  // rate is the price of this sell-order
  lazy val rate = Rational(amountB, amountS)

  def withOutstandingAmountS(v: BigInt) = {
    val r = Rational(v, amountS)
    copy(_outstanding = Some(original.scaleBy(r)))
  }

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
      copy(_reserved = Some(
        OrderState(reservedAmountS, 0, v - reservedAmountS)
      )).updateActual()
    } else if (token == tokenS && tokenFee != tokenS) {
      copy(_reserved = Some(
        OrderState(v, 0, reserved.amountFee)
      )).updateActual()
    } else if (token != tokenS && tokenFee == tokenB) {
      copy(_reserved = Some(
        OrderState(reserved.amountS, 0, v)
      )).updateActual()
    } else {
      copy(_reserved = Some(
        OrderState(reserved.amountS, 0, v)
      )).updateActual()
    }

  // Private methods
  private[core] def as(status: XOrderStatus) = {
    assert(status != PENDING)
    copy(
      status = status,
      _reserved = None,
      _actual = None,
      _matchable = None
    )
  }

  private[core] def resetMatchable() = copy(_matchable = None)

  private[core] def displayableAmountS()(implicit tokenMetadataManager: TokenMetadataManager) =
    calcDisplayableAmount(tokenS, actual.amountS)

  private[core] def displayableAmountB()(implicit tokenMetadataManager: TokenMetadataManager) =
    calcDisplayableAmount(tokenB, actual.amountB)

  private[core] def displayableAmountFee()(implicit tokenMetadataManager: TokenMetadataManager) =
    calcDisplayableAmount(tokenFee, actual.amountFee)

  private[core] def isSell()(implicit marketId: XMarketId) =
    (tokenS == marketId.secondary)

  private[core] def displayablePrice()(
    implicit
    marketId: XMarketId,
    tokenMetadataManager: TokenMetadataManager
  ) = {
    if (tokenS == marketId.secondary) Rational(amountS, amountB).doubleValue
    else Rational(amountB, amountS).doubleValue
  }

  private[core] def displayableAmount()(
    implicit
    marketId: XMarketId, tokenMetadataManager: TokenMetadataManager
  ) = {
    if (tokenS == marketId.secondary) displayableAmountS
    else displayableAmountB
  }

  private[core] def displayableTotal()(
    implicit
    marketId: XMarketId,
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
    copy(_actual = Some(original.scaleBy(r)))
  }

  private def calcDisplayableAmount(token: String, amount: BigInt)(
    implicit
    tokenMetadataManager: TokenMetadataManager
  ) = {
    if (!tokenMetadataManager.hasToken(token)) {
      throw new IllegalStateException(s"no metadata available for token $token")
    }
    val metadata = tokenMetadataManager.getToken(token).get
    val decimals = metadata.decimals
    (Rational(amount) / Rational(Math.pow(10, decimals))).doubleValue
  }
}
