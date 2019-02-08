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

package io.lightcone.core

import com.google.protobuf.ByteString
import org.web3j.utils.Numeric

class Address(val value: BigInt) {

  assert(value <= BigInt("f" * 40, 16))

  def toBigInt: BigInt = value

  def isZero = (value == BigInt(0))
  def isEmpty = (value == BigInt(0))

  def toBytes: Array[Byte] = {
    Numeric.toBytesPadded(value.bigInteger, 20)
  }

  def toByteString: ByteString = {
    ByteString.copyFrom(value.toByteArray)
  }

  override def equals(obj: Any): Boolean =
    obj match {
      case addr: Address =>
        value.equals(addr.value)
      case _ =>
        false
    }

  override def hashCode(): Int = value.hashCode()

  override def toString() = {
    Numeric.toHexStringWithPrefixZeroPadded(value.bigInteger, 40)
  }

}

object Address {

  val ZERO = Address(0)

  val MAX = Address(BigInt("f" * 40, 16))

  def apply(value: BigInt): Address = new Address(value)

  def apply(bytes: Array[Byte]): Address = {
    assert(bytes.length <= 20)
    new Address(Numeric.toBigInt(bytes))
  }

  def apply(byteString: ByteString): Address = {
    apply(byteString.toByteArray)
  }

  def apply(addr: String): Address = {
    apply(Numeric.toBigInt(formatHex(addr)))
  }

  def isValid(obj: Any): Boolean = {
    obj match {
      case add: String =>
        Numeric.hexStringToByteArray(add).length <= 20
      case add: Array[Byte] =>
        add.length <= 20
      case add: BigInt =>
        add <= MAX.value
      case add: ByteString =>
        add.size() <= 20
      case _ =>
        false
    }
  }

  def normalize(address: String): String =
    try {
      Address(address).toString
    } catch {
      case _: Throwable =>
        throw ErrorException(
          ErrorCode.ERR_ETHEREUM_ILLEGAL_ADDRESS,
          message = s"invalid ethereum address:$address"
        )
    }

}
