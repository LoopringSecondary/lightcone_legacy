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

import com.google.protobuf.ByteString
import org.web3j.utils.Numeric
import org.web3j.crypto.WalletUtils

class Address(val value: BigInt) {

  override def toString =
    Numeric.toHexStringWithPrefixZeroPadded(value.bigInteger, 40)

  def toBigInt: BigInt = {
    this.value
  }

  def toBytes: Array[Byte] = {
    Numeric.toBytesPadded(value.bigInteger, 20)
  }

  def toByteString: ByteString = {
    ByteString.copyFrom(this.value.toByteArray)
  }

  override def equals(obj: Any): Boolean =
    obj match {
      case add: Address ⇒
        this.value.equals(add.value)
      case _ ⇒
        false
    }

  override def hashCode(): Int = {
    this.value.hashCode()
  }
}

object Address {
  val maxAddress: BigInt = BigInt("f" * 40, 16)

  def apply(bytes: Array[Byte]): Address = {
    assert(bytes.length <= 20)
    new Address(Numeric.toBigInt(bytes))
  }

  def apply(byteString: ByteString): Address = {
    apply(byteString.toByteArray)
  }

  def apply(value: BigInt): Address = {
    assert(value <= maxAddress)
    new Address(value)
  }

  def apply(addr: String): Address =
    apply(Numeric.toBigInt(addr))

  def isValid(obj: Any): Boolean = {
    obj match {
      case add: String ⇒
        Numeric.hexStringToByteArray(add).length <= 20
      case add: Array[Byte] ⇒
        add.length <= 20
      case add: BigInt ⇒
        add <= maxAddress
      case add: ByteString ⇒
        add.size() <= 20
      case _ ⇒
        false
    }
  }

}
