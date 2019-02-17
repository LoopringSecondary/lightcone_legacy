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

package io.lightcone.lib

import org.scalatest._
import org.web3j.utils.Numeric
import com.google.protobuf.ByteString

class AddressSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "stringAddress" should "be canonicalized" in {
    val addr1 = Address("0x000ee35D70AD6331000E370F079aD7df52E75005")
    val addr2 = Address("0x00000ee35d70ad6331000e370f079ad7df52e75005")
    info(addr1.toString)
    assert(addr1.equals(addr2))
  }

  "byteArrayAddress" should "be canonicalized" in {
    val bytes = Numeric.hexStringToByteArray("f" * 40)
    info(bytes.length.toString)
    val address = Address(bytes)
    info(address.toString)
    info(address.toBytes.length.toString)
  }

  "bigIntAddress" should "be canonicalized" in {
    val bigIntAdd = BigInt(
      Numeric.cleanHexPrefix("0x000ee35D70AD6331000E370F079aD7df52E75005"),
      16
    )
    val address = Address(bigIntAdd)
    info(address.toString)
  }

  "addressValid" should "correctly check validity" in {
    val add1 = "0x000ee35D70AD6331000E370F079aD7df52E75005"
    val add2 = "0x1"
    val add3 = "0x" + "0" * 41

    // String address
    Address.isValid(add1) should be(true)
    Address.isValid(add2) should be(true)
    Address.isValid(add3) should be(false)

    //BigInt
    Address.isValid(BigInt(Numeric.toBigInt(add1))) should be(true)
    Address.isValid(BigInt(Numeric.toBigInt(add2))) should be(true)
    Address.isValid(BigInt(Numeric.toBigInt(add3))) should be(true)

    //Array[Byte]
    Address.isValid(Numeric.hexStringToByteArray(add1)) should be(true)
    Address.isValid(Numeric.hexStringToByteArray(add2)) should be(true)
    Address.isValid(Numeric.hexStringToByteArray(add3)) should be(false)

    //ByteString
    Address.isValid(ByteString.copyFrom(Numeric.hexStringToByteArray(add1))) should be(
      true
    )
    Address.isValid(ByteString.copyFrom(Numeric.hexStringToByteArray(add2))) should be(
      true
    )
    Address.isValid(ByteString.copyFrom(Numeric.hexStringToByteArray(add3))) should be(
      false
    )

  }

}
