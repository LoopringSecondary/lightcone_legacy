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

import org.web3j.crypto._
import org.web3j.tx.ChainId
import org.web3j.utils.Numeric

class RingSignerImpl(
    protocol: String = "",
    chainId: Byte = 0.toByte,
    privateKey: String = "0x",
    feeReceipt: String = "",
    lrcAddress: String = "0xef68e7c694f40c8202821edf525de3782458639f"
) extends RingSigner {
  val credentials: Credentials = Credentials.create(privateKey)

  implicit val ringSerializer = new RingSerializerImpl(lrcAddress)

  def getSignerAddress(): String = credentials.getAddress

  def getInputData(ring: Ring): String = {
    val signatureData = Sign.signMessage(
      Numeric.hexStringToByteArray(ring.hash),
      credentials.getEcKeyPair
    )
    val sigBytes = signatureData.getR ++ signatureData.getS

    var lRing = ring.copy(
      feeReceipt = feeReceipt,
      miner = credentials.getAddress,
      sig = Numeric.toHexString(sigBytes)
    )
    lRing.getInputData()
  }

  def getSignedTxData(
    inputData: String,
    nonce: BigInt,
    gasLimit: BigInt,
    gasPrice: BigInt
  ): Array[Byte] = {
    val rawTransaction = RawTransaction.createTransaction(
      nonce.bigInteger,
      gasPrice.bigInteger,
      gasLimit.bigInteger,
      protocol,
      BigInt(0).bigInteger,
      inputData
    )
    signTx(rawTransaction)
  }

  private def signTx(rawTransaction: RawTransaction): Array[Byte] = {
    if (chainId > ChainId.NONE) {
      TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
    } else {
      TransactionEncoder.signMessage(rawTransaction, credentials)
    }
  }

}
