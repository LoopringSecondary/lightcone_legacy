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

package org.loopring.lightcone.ethereum.data

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
    // optional
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

  lazy val cryptoHash = {
    val packer = new BytesPacker()
    packer.addUint(amountS)
    packer.addUint(amountB)
    packer.addUint(feeAmount)
    packer.addUint(BigInt(validSince))
    packer.addUint(BigInt(validUntil))
    packer.addAddress(owner, true)
    packer.addAddress(tokenS, true)
    packer.addAddress(tokenB, true)
    packer.addAddress(dualAuthAddress, true)
    packer.addAddress(broker,true)
    packer.addAddress(orderInterceptor, true)
    packer.addAddress(wallet, true)
    packer.addAddress(tokenReceipt, true)
    packer.addAddress(feeToken, true)
    packer.addUint16(walletSplitPercentage)
    packer.addUint16(tokenSFeePercentage)
    packer.addUint16(tokenBFeePercentage)
    packer.addBoolean(allOrNone)

    Numeric.toHexString(web3Hash.sha3(packer.getPackedBytes))
  }

}
