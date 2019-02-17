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

package io.lightcone

import java.math.BigInteger
import io.lightcone.core._
import io.lightcone.lib._
import org.web3j.crypto._
import org.web3j.utils.Numeric
import org.web3j.crypto.WalletUtils.isValidAddress

package object ethereum {

  // TODO(dongw): move these methods to a class.
  def verifyEthereumSignature(
    hash: Array[Byte],
    r: Array[Byte],
    s: Array[Byte],
    v: Byte,
    addr: Address): Boolean = {
    val signatureDataV = new Sign.SignatureData(v, r, s)
    val key = Sign.signedPrefixedMessageToKey(hash, signatureDataV)
    addr.equals(Address(Keys.getAddress(key)))
  }

  def verifySignature(
    hash: Array[Byte],
    r: Array[Byte],
    s: Array[Byte],
    v: Byte,
    addr: Address): Boolean = {
    val signatureDataV = new Sign.SignatureData(v, r, s)
    val key = Sign.signedMessageToKey(hash, signatureDataV)
    addr.equals(Address(Keys.getAddress(key)))
  }

  def verifySignature(
    hash: Array[Byte],
    sig: Array[Byte],
    addr: Address): Boolean = {
    if (sig.length == 65) {
      val r = sig.toSeq.slice(0, 32).toArray
      val s = sig.toSeq.slice(32, 64).toArray
      val v = sig(64)
      verifySignature(hash, r, s, v, addr)
    } else {
      false
    }
  }

  def getSignedTxData(tx: Tx)(implicit credentials: Credentials): String = {
    val rawTransaction = RawTransaction
      .createTransaction(
        BigInteger.valueOf(tx.nonce),
        tx.gasPrice.bigInteger,
        tx.gasLimit.bigInteger,
        tx.to,
        tx.value.bigInteger,
        tx.inputData)
    Numeric.toHexString(
      TransactionEncoder
        .signMessage(rawTransaction, tx.chainId.toByte, credentials))
  }

  def isAddressValidAndNonZero(addr: String) = addr match {
    case ad if isValidAddress(addr) =>
      BigInt(Numeric.cleanHexPrefix(ad), 16) > 0
    case _ => false
  }

  // ----- implicit methods -----
  implicit def int2BigInt(x: Int): BigInt = BigInt(x)
  implicit def string2BigInt(x: String): BigInt = NumericConversion.toBigInt(x)
}
