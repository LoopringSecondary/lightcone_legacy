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

package io.lightcone.relayer

import io.lightcone.core._
import io.lightcone.ethereum.event._
import io.lightcone.lib._
import io.lightcone.core.ErrorCode._

package object data {

  case class BalanceAndAllowanceBigInt(
      balance: BigInt,
      allowance: BigInt)

  implicit class RichEventHeader(header: EventHeader) {

    def sequenceId() = {
      if (header.blockNumber > 500000000) // < pow(2, 29)
        throw ErrorException(
          ERR_INTERNAL_UNKNOWN,
          s"blockNumber >= 500000000 in ${header}"
        )
      if (header.txIndex >= 4096 || header.logIndex >= 4096) // pow(2, 12)
        throw ErrorException(
          ERR_INTERNAL_UNKNOWN,
          s"txIndex or logIndex >= 4096 in ${header}"
        )
      if (header.eventIndex >= 1024) // pow(2, 10)
        throw ErrorException(
          ERR_INTERNAL_UNKNOWN,
          s"eventIndex >= 1024 in ${header}"
        )
      val b: Long = header.blockNumber << 34
      val t: Long = header.txIndex.toLong << 22
      val l: Long = header.logIndex.toLong << 10
      b + t + l + header.eventIndex
    }
  }

  implicit def balanceAndAlowance2BalanceAndAlowanceBigInt(
      xba: BalanceAndAllowance
    ): BalanceAndAllowanceBigInt =
    BalanceAndAllowanceBigInt(balance = xba.balance, allowance = xba.allowance)

  implicit def balanceAndAlowanceBigInt2BalanceAndAlowance(
      ba: BalanceAndAllowanceBigInt
    ): BalanceAndAllowance =
    BalanceAndAllowance(balance = ba.balance, allowance = ba.allowance)

  implicit def order2Matchable(order: Order): Matchable =
    Matchable(
      id = order.id,
      tokenS = order.tokenS,
      tokenB = order.tokenB,
      tokenFee = order.tokenFee,
      amountS = order.amountS,
      amountB = order.amountB,
      amountFee = order.amountFee,
      validSince = order.validSince,
      submittedAt = order.submittedAt,
      numAttempts = order.numAttempts,
      status = order.status,
      walletSplitPercentage = order.walletSplitPercentage,
      _outstanding = order.outstanding.map(orderState2MatchableState),
      _reserved = order.reserved.map(orderState2MatchableState),
      _actual = order.actual.map(orderState2MatchableState),
      _matchable = order.matchable.map(orderState2MatchableState)
    )

  implicit def matchable2Order(matchable: Matchable): Order =
    Order(
      id = matchable.id,
      tokenS = matchable.tokenS,
      tokenB = matchable.tokenB,
      tokenFee = matchable.tokenFee,
      amountS = matchable.amountS,
      amountB = matchable.amountB,
      amountFee = matchable.amountFee,
      validSince = matchable.validSince,
      submittedAt = matchable.submittedAt,
      numAttempts = matchable.numAttempts,
      status = matchable.status,
      walletSplitPercentage = matchable.walletSplitPercentage,
      outstanding = matchable._outstanding.map(matchableState2OrderState),
      reserved = matchable._reserved.map(matchableState2OrderState),
      actual = matchable._actual.map(matchableState2OrderState),
      matchable = matchable._matchable.map(matchableState2OrderState)
    )

  implicit def orderState2MatchableState(
      orderState: OrderState
    ): MatchableState =
    MatchableState(
      amountS = orderState.amountS,
      amountB = orderState.amountB,
      amountFee = orderState.amountFee
    )

  implicit def matchableState2OrderState(
      MatchableState: MatchableState
    ): OrderState =
    OrderState(
      amountS = MatchableState.amountS,
      amountB = MatchableState.amountB,
      amountFee = MatchableState.amountFee
    )

  implicit def matchableRing2OrderRing(orderRing: MatchableRing): OrderRing =
    OrderRing(maker = Some(orderRing.maker), taker = Some(orderRing.taker))

  implicit def orderRing2MatchableRing(orderRing: OrderRing): MatchableRing =
    MatchableRing(maker = orderRing.getMaker, taker = orderRing.getTaker)

  implicit def seqMatchableRing2OrderRing(
      orderRings: Seq[MatchableRing]
    ): Seq[OrderRing] =
    orderRings map { orderRing =>
      OrderRing(maker = Some(orderRing.maker), taker = Some(orderRing.taker))
    }

  implicit def seqOrderRing2MatchableRing(
      orderRings: Seq[OrderRing]
    ): Seq[MatchableRing] =
    orderRings map { orderRing =>
      MatchableRing(maker = orderRing.getMaker, taker = orderRing.getTaker)
    }

  implicit def expectedMatchableFill2ExpectedOrderFill(
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

  implicit class RichMarketPair(marketPair: MarketPair) {
    def hashString = MarketHash(marketPair).hashString
    def longId = MarketHash(marketPair).longId

    def normalize() =
      MarketPair(
        baseToken = Address.normalize(marketPair.baseToken),
        quoteToken = Address.normalize(marketPair.quoteToken)
      )
  }

  implicit class RichRawOrder(order: RawOrder) {

    def toOrder(): Order =
      Order(
        id = order.hash,
        tokenS = order.tokenS,
        tokenB = order.tokenB,
        tokenFee = order.getFeeParams.tokenFee,
        amountS = order.amountS,
        amountB = order.amountB,
        amountFee = order.getFeeParams.amountFee,
        submittedAt = order.getState.createdAt,
        status = order.getState.status,
        walletSplitPercentage = order.getFeeParams.waiveFeePercentage / 1000.0
      )

    def withStatus(newStatus: OrderStatus): RawOrder = {
      val state = order.getState.copy(status = newStatus)
      order.copy(state = Some(state))
    }

    def getMarketHash() =
      MarketHash(MarketPair(order.tokenS, order.tokenB)).hashString
  }
}
