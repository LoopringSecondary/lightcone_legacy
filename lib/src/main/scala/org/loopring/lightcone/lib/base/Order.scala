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

package org.loopring.lightcone.lib

import org.web3j.crypto.{ Hash ⇒ web3Hash }
import org.web3j.utils.Numeric

case class Order(
    owner: String,
    tokenS: String,
    tokenB: String,
    amountS: BigInt,
    amountB: BigInt,
    validSince: Long,
    allOrNone: Boolean,
    feeToken: String,
    feeAmount: BigInt,
    tokenReceipt: String,
    sig: String,
    dualAuthSig: String,
    // option
    hash: String = "",
    validUntil: Long = 0,
    wallet: String = "0x0",
    dualAuthAddress: String = "0x0",
    broker: String = "0x0",
    orderInterceptor: String = "0x0",
    version: Int = 0,
    walletSplitPercentage: Int = 0,
    tokenSFeePercentage: Int = 0,
    tokenBFeePercentage: Int = 0,
    waiveFeePercentage: Int = 0,
    tokenSpendableS: BigInt = 0,
    tokenSpendableFee: BigInt = 0,
    brokerSpendableS: BigInt = 0,
    brokerSpendableFee: BigInt = 0,
) {

  def generateHash: String = {
    val data = ByteStream()
    data.addUint(amountS)
    data.addUint(amountB)
    data.addUint(feeAmount)
    data.addUint(BigInt(validSince))
    data.addUint(BigInt(validUntil))
    data.addAddress(owner, true)
    data.addAddress(tokenS, true)
    data.addAddress(tokenB, true)
    data.addAddress(dualAuthAddress, true)
    data.addAddress(broker,true)
    data.addAddress(orderInterceptor, true)
    data.addAddress(wallet, true)
    data.addAddress(tokenReceipt, true)
    data.addAddress(feeToken, true)
    data.addUint16(walletSplitPercentage)
    data.addUint16(tokenSFeePercentage)
    data.addUint16(tokenBFeePercentage)
    data.addBoolean(allOrNone)

    Numeric.toHexString(web3Hash.sha3(data.getBytes))
  }

}
