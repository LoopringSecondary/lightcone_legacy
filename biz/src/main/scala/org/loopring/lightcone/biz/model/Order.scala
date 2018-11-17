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

package org.loopring.lightcone.biz.model

import org.loopring.lightcone.biz.enum.MarketSide.MarketSide
import org.loopring.lightcone.biz.enum.OrderStatus
import org.loopring.lightcone.biz.enum.OrderStatus._
import org.loopring.lightcone.biz.enum.OrderType.OrderType
import org.loopring.lightcone.biz.enum.SoftCancelType.SoftCancelType

case class OrderQuery(statuses: Seq[OrderStatus], owner: String, market: String, hashes: Seq[String], sideOpt: Option[MarketSide], orderTypeOpt: Option[OrderType])

case class CancelOrderOption(hash: String, cutoffTime: Long, market: String, cancelType: SoftCancelType, owner: String)

case class Order(
    var rawOrder: RawOrder = RawOrder(),
    updatedBlock: Long = 0,
    dealtAmountS: String = "",
    dealtAmountB: String = "",
    cancelledAmountS: String = "",
    cancelledAmountB: String = "",
    status: OrderStatus = OrderStatus.NEW,
    broadcastTime: Int = 0,
    powNonce: Long = 0,
    market: String = "",
    side: String = "",
    price: Double = 0,
    orderType: String = ""
)

case class RawOrder(
    var rawOrderEssential: RawOrderEssential = RawOrderEssential(),
    version: String = "",
    tokenSpendableS: String = "",
    tokenSpendableFee: String = "",
    brokerSpendableS: String = "",
    brokerSpendableFee: String = "",
    sig: String = "",
    dualAuthSig: String = "",
    waiveFeePercentage: Int = 0,
    dualPrivateKey: String = ""
)

case class RawOrderEssential(
    owner: String = "",
    tokenS: String = "",
    tokenB: String = "",
    amountS: String = "",
    amountB: String = "",
    validSince: Long = 0,
    dualAuthAddress: String = "",
    broker: String = "",
    orderInterceptor: String = "",
    wallet: String = "",
    validUntil: Long = 0,
    allOrNone: Boolean = false,

    feeToken: String = "",
    feeAmount: String = "",
    feePercentage: Int = 0,
    tokenSFeePercentage: Int = 0,
    tokenBFeePercentage: Int = 0,
    tokenRecipient: String = "",
    walletSplitPercentage: Int = 0,
    hash: String = ""
)

