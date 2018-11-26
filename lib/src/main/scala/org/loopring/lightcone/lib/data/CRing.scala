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

import org.web3j.utils.Numeric
import org.web3j.crypto.{ Hash ⇒ web3Hash }

import org.loopring.lightcone.lib.abi.RingSubmitterABI

// contract Ring 避免数据结构命名重复
// lib里的数据结构原则上不参与数据传输
case class CRing(
    feeReceipt: String,
    miner: String,
    sig: String,
    ringOrderIndex: Seq[Seq[Int]], // todo change to map
    orders: Seq[COrder],
    transactionOrigin: String
) {

  lazy val cryptoHash = {
    val stream = new BytesPacker()
    orders.foreach { order ⇒
      stream.addPadHex(order.hash)
      stream.addUint16(order.waiveFeePercentage)
    }
    Numeric.toHexString(web3Hash.sha3(stream.getPackedBytes()))
  }

  def getInputData(algorithm: SignAlgorithm.Value)(implicit serializer: RingSerializer, abi: RingSubmitterABI, signer: Signer): String = {
    val ringHash = this.cryptoHash

    val signatureData = signer.signHash(algorithm, ringHash)

    val lRing = this.copy(
      feeReceipt = feeReceipt,
      miner = signer.address,
      sig = signatureData
    )

    val data = serializer.serialize(lRing)
    val bytes = Numeric.hexStringToByteArray(data)
    val encode = abi.submitRing.encode(bytes)
    Numeric.toHexString(encode)
  }

}
