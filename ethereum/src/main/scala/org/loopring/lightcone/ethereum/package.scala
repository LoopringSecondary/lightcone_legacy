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

package org.loopring.lightcone.ethereum

import java.math.BigInteger

import com.google.protobuf.ByteString
import org.loopring.lightcone.ethereum.data.Address
import org.web3j.crypto._
import org.web3j.utils.Numeric

package object ethereum {
  implicit def int2BigInt(x: Int): BigInt = BigInt(x)

  implicit def string2BigInt(x: String): BigInt = x match {
    case n if n.length == 0 ⇒ BigInt(0)
    case p if p.startsWith("0x") ⇒ BigInt(p, 16)
    case _ ⇒ BigInt(x, 16)
  }

  implicit def byteString2BigInt(bs: ByteString): BigInt =
    string2BigInt(bs.toStringUtf8)

  def verifySignature(
      hash: Array[Byte],
      r: Array[Byte],
      s: Array[Byte],
      v: Int,
      add: Address
    ): Boolean = {
    val signatureDataV = new Sign.SignatureData(v.toByte, r, s)
    val key = Sign.signedMessageToKey(hash, signatureDataV)
    add.equals(Address(Keys.getAddress(key)))
  }

  def getSignedTxData(
      inputData: String,
      nonce: Int,
      gasLimit: BigInt,
      gasPrice: BigInt,
      to: String,
      chainId: Int = 1,
      privateKey: String
    ): String = {
    val rawTransaction = RawTransaction
      .createTransaction(
        BigInteger.valueOf(nonce),
        gasPrice.bigInteger,
        gasLimit.bigInteger,
        to,
        BigInteger.ZERO,
        inputData
      )
    val credentials = Credentials.create(privateKey)
    Numeric.toHexString(
      TransactionEncoder
        .signMessage(rawTransaction, chainId.toByte, credentials)
    )
  }

}
