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

package org.loopring.lightcone.lib.data

import org.web3j.crypto.{Hash ⇒ web3Hash}
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
    val EIP712_HEADER = "0x1901"
    val EIP712_ORDER_SCHEMA_HASH = "0x5632ff1bdfbe9ca7ecbcb1bd8c61f364e0debfed45fd8be4e459081586292fff"
    val EIP712_DOMAIN_HASH = "0xaea25658c273c666156bd427f83a666135fcde6887a6c25fc1cd1562bc4f3f34"

    // 第一层数据 19 * 32
    val packer = new BytesPacker()
    packer.addPadHex(EIP712_ORDER_SCHEMA_HASH)
    packer.addUint(amountS)
    packer.addUint(amountB)
    packer.addUint(feeAmount)
    packer.addUint(BigInt(validSince))
    packer.addUint(BigInt(validUntil))
    packer.addPadHex(owner)
    packer.addPadHex(tokenS)
    packer.addPadHex(tokenB)
    packer.addPadHex(dualAuthAddress)
    packer.addPadHex(broker)
    packer.addPadHex(orderInterceptor)
    packer.addPadHex(wallet)
    packer.addPadHex(tokenReceipt)
    packer.addPadHex(feeToken)
    packer.addUint(walletSplitPercentage)
    packer.addUint(tokenSFeePercentage)
    packer.addUint(tokenBFeePercentage)
    packer.addUint(if (allOrNone) 1 else 0)

    val message = Numeric.toHexString(web3Hash.sha3(packer.getPackedBytes))

    // 第二层数据 eip组装
    // encodePacked 需要根据不同类型确定数据长度 这里我们只用到一个数据类型
    // ex:
    // abi.encodePacked(uint8(0x42), uint256(0x1337), "AAAA", "BBBB")
    // 0x42
    // 0x0000000000000000000000000000000000000000000000000000000000001337
    // 0x41414141
    // 0x42424242
    val eip = new BytesPacker
    eip.addHex(EIP712_HEADER)
    eip.addHex(EIP712_DOMAIN_HASH)
    eip.addHex(message)

    Numeric.toHexString(web3Hash.sha3(eip.getPackedBytes()))
  }

}
