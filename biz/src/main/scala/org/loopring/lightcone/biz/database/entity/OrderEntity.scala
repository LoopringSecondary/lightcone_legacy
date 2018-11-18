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

package org.loopring.lightcone.biz.database.entity

import org.loopring.lightcone.biz.database.base.BaseEntity

case class OrderEntity(
    id: Long = 0,
    updatedAt: Long = 0,
    createdAt: Long = 0,
    version: String = "",
    owner: String = "",
    tokenS: String = "",
    tokenB: String = "",
    amountS: String = "",
    amountB: String = "",
    validSince: Long = 0,
    tokenSpendableS: String = "",
    tokenSpendableFee: String = "",
    dualAuthAddress: String = "",
    broker: String = "",
    brokerSpendableS: String = "",
    brokerSpendableFee: String = "",
    orderInterceptor: String = "",
    wallet: String = "",
    validUntil: Long = 0,
    sig: String = "",
    dualAuthSig: String = "",
    allOrNone: Boolean = false,
    feeToken: String = "",
    feeAmount: String = "",
    feePercentage: Int = 0,
    waiveFeePercentage: Int = 0,
    tokenSFeePercentage: Int = 0,
    tokenBFeePercentage: Int = 0,
    tokenRecipient: String = "",
    walletSplitPercentage: Int = 0,
    dualPrivateKey: String = "",
    hash: String = "",
    powNonce: Long = 0,
    updatedBlock: Long = 0,
    dealtAmountS: String = "",
    dealtAmountB: String = "",
    cancelledAmountS: String = "",
    cancelledAmountB: String = "",
    status: String = "",
    broadcastTime: Int = 0,
    market: String = "",
    side: String = "",
    price: Double = 0,
    orderType: String = ""
) extends BaseEntity

