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

import java.math.BigInteger

import com.google.protobuf.ByteString
import org.web3j.utils.Numeric

class Address(val value: BigInt) {

  override def toString = {
    val valueHex = value.toString(16)
    "0x" + "0" * (40 - valueHex.length) + value.toString(16)
  }

  def toBigInt = {
    this.value
  }

  def toBytes = {
    this.value.toByteArray
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
}

object Address {
  def apply(bytes: Array[Byte]): Address = {
    assert(bytes.length <= 80)
    new Address(Numeric.toBigInt(bytes))
  }

  def apply(byteString: ByteString): Address = {
    apply(byteString.toByteArray)
  }

  def apply(value: BigInt): Address = {
    assert(value.<=(BigInt("f" * 40, 16)))
    new Address(value)
  }

  def apply(addr: String): Address =
    apply(BigInt(Numeric.cleanHexPrefix(addr), 16))

}
