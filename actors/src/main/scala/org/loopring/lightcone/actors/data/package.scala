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

package org.loopring.lightcone.actors

import com.google.protobuf.ByteString
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto.OrderStatus._
import org.loopring.lightcone.proto._

package object data {

  case class BalanceAndAllowance(
      balance: BigInt,
      allowance: BigInt)

  ///////////

  implicit def byteString2BigInt(bytes: ByteString): BigInt = {
    if (bytes.size() > 0) BigInt(bytes.toByteArray)
    else BigInt(0)
  }

  implicit def bigInt2ByteString(b: BigInt): ByteString =
    ByteString.copyFrom(b.toByteArray)

  implicit def byteArray2ByteString(bytes: Array[Byte]) =
    ByteString.copyFrom(bytes)

  implicit def xBalanceAndAlowance2BalanceAndAlowance(
      xba: XBalanceAndAllowance
    ): BalanceAndAllowance =
    BalanceAndAllowance(balance = xba.balance, allowance = xba.allowance)

  implicit def balanceAndAlowance2XBalanceAndAlowance(
      ba: BalanceAndAllowance
    ): XBalanceAndAllowance =
    XBalanceAndAllowance(balance = ba.balance, allowance = ba.allowance)

  implicit def order2Matchable(xorder: XOrder): Matchable =
    Matchable(
      id = xorder.id,
      tokenS = xorder.tokenS,
      tokenB = xorder.tokenB,
      tokenFee = xorder.tokenFee,
      amountS = xorder.amountS,
      amountB = xorder.amountB,
      amountFee = xorder.amountFee,
      createdAt = xorder.createdAt,
      updatedAt = xorder.updatedAt,
      status = xorder.status,
      walletSplitPercentage = xorder.walletSplitPercentage,
      _outstanding = xorder.outstanding.map(orderState2MatchableState),
      _reserved = xorder.reserved.map(orderState2MatchableState),
      _actual = xorder.actual.map(orderState2MatchableState),
      _matchable = xorder.matchable.map(orderState2MatchableState)
    )

  implicit def matchable2Order(matchable: Matchable): XOrder =
    XOrder(
      id = matchable.id,
      tokenS = matchable.tokenS,
      tokenB = matchable.tokenB,
      tokenFee = matchable.tokenFee,
      amountS = matchable.amountS,
      amountB = matchable.amountB,
      amountFee = matchable.amountFee,
      createdAt = matchable.createdAt,
      updatedAt = matchable.updatedAt,
      status = matchable.status,
      walletSplitPercentage = matchable.walletSplitPercentage,
      outstanding = matchable._outstanding.map(matchableState2OrderState),
      reserved = matchable._reserved.map(matchableState2OrderState),
      actual = matchable._actual.map(matchableState2OrderState),
      matchable = matchable._matchable.map(matchableState2OrderState)
    )

  implicit def orderState2MatchableState(
      xOrderState: OrderState
    ): MatchableState =
    MatchableState(
      amountS = xOrderState.amountS,
      amountB = xOrderState.amountB,
      amountFee = xOrderState.amountFee
    )

  implicit def matchableState2OrderState(
      orderState: MatchableState
    ): OrderState =
    OrderState(
      amountS = orderState.amountS,
      amountB = orderState.amountB,
      amountFee = orderState.amountFee
    )

  implicit def matchableRing2OrderRing(orderRing: MatchableRing): OrderRing =
    OrderRing(maker = Some(orderRing.maker), taker = Some(orderRing.taker))

  implicit def orderRing2MatchableRing(xOrderRing: OrderRing): MatchableRing =
    MatchableRing(maker = xOrderRing.getMaker, taker = xOrderRing.getTaker)

  implicit def seqMatchableRing2OrderRing(
      orderRings: Seq[MatchableRing]
    ): Seq[OrderRing] =
    orderRings map { orderRing =>
      OrderRing(maker = Some(orderRing.maker), taker = Some(orderRing.taker))
    }

  implicit def seqOrderRing2MatchableRing(
      xOrderRings: Seq[OrderRing]
    ): Seq[MatchableRing] =
    xOrderRings map { xOrderRing =>
      MatchableRing(maker = xOrderRing.getMaker, taker = xOrderRing.getTaker)
    }

  implicit def expectFill2XEcpectFill(
      fill: ExpectedMatchableFill
    ): ExpectedOrderFill =
    ExpectedOrderFill(
      order = Some(fill.order),
      pending = Some(fill.pending),
      amountMargin = fill.amountMargin
    )

  implicit def expectedOrderFill2ExpectedMatchableFill(
      fill: ExpectedOrderFill
    ): ExpectedMatchableFill =
    ExpectedMatchableFill(
      order = fill.getOrder,
      pending = fill.getPending,
      amountMargin = fill.amountMargin
    )

  implicit def expectedMatchableFill2ExpectedOrderFill(
      xraworder: RawOrder
    ): XOrder = {

    val feeParams = xraworder.feeParams.getOrElse(RawOrder.FeeParams())
    XOrder(
      id = xraworder.hash,
      tokenS = xraworder.tokenS,
      tokenB = xraworder.tokenB,
      tokenFee = feeParams.tokenFee,
      amountS = xraworder.amountS,
      amountB = xraworder.amountB,
      amountFee = feeParams.amountFee,
      createdAt = xraworder.getState.createdAt,
      updatedAt = xraworder.getState.updatedAt,
      status = xraworder.getState.status,
      walletSplitPercentage = feeParams.waiveFeePercentage / 1000.0
    )
  }

  implicit def convertOrderStatusToErrorCode(status: OrderStatus): ErrorCode =
    status match {
      case STATUS_INVALID_DATA              => ERR_INVALID_ORDER_DATA
      case STATUS_UNSUPPORTED_MARKET        => ERR_INVALID_MARKET
      case STATUS_CANCELLED_TOO_MANY_ORDERS => ERR_TOO_MANY_ORDERS
      case STATUS_CANCELLED_DUPLICIATE      => ERR_ORDER_ALREADY_EXIST
      case _                                => ERR_INTERNAL_UNKNOWN
    }
}
