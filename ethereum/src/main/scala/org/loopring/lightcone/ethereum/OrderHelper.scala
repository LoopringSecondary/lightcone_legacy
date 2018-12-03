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

import org.web3j.crypto.{ Hash ⇒ Web3Hash }
import org.web3j.utils.Numeric
import org.loopring.lightcone.proto.core._

object OrderHelper {
  def calculateHash(order: XRawOrder): XRawOrder = {
    val bitstream = new Bitstream
    bitstream.addUintStr(order.amountS.toString)
    bitstream.addUintStr(order.amountB.toString)
    bitstream.addUintStr(order.feeParams.get.feeAmount.toString)
    bitstream.addUint(BigInt(order.validSince))
    bitstream.addUint(BigInt(order.params.get.validUntil))
    bitstream.addAddress(order.owner, true)
    bitstream.addAddress(order.tokenS, true)
    bitstream.addAddress(order.tokenB, true)
    bitstream.addAddress(order.params.get.dualAuthAddr, true)
    bitstream.addAddress(order.params.get.broker, true)
    bitstream.addAddress(order.params.get.orderInterceptor, true)
    bitstream.addAddress(order.params.get.wallet, true)
    bitstream.addAddress(order.feeParams.get.tokenRecipient, true)
    bitstream.addAddress(order.feeParams.get.feeToken, true)
    bitstream.addUint16(order.feeParams.get.walletSplitPercentage)
    bitstream.addUint16(order.feeParams.get.tokenSFeePercentage)
    bitstream.addUint16(order.feeParams.get.tokenBFeePercentage)
    bitstream.addBoolean(order.params.get.allOrNone)

    order.copy(hash = Numeric.toHexString(Web3Hash.sha3(bitstream.getPackedBytes)))
  }

  def sign(order: XRawOrder): XRawOrder = {
    order.copy()
  }
}
