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

package org.loopring.lightcone.lib

import org.web3j.utils.Numeric

case class ByteParser(x: String) {

  val data = Numeric.cleanHexPrefix(x)

  def extractUint8(offset: Int): Int = extractNumber(offset, 1).intValue()

  def extractUint16(offset: Int): Int = extractNumber(offset, 2).intValue()

  def extractUint32(offset: Int): Int = extractNumber(offset, 4).intValue()

  def extractUint(offset: Int): BigInt = extractNumber(offset, 32)

  def extractAddress(offset: Int): String = Numeric.toHexString(extractBytesX(offset, 20))

  def extractBytes1(offset: Int): Array[Byte] = extractBytesX(offset, 1)

  def extractBytes32(offset: Int): Array[Byte] = extractBytesX(offset, 32)

  def extractNumber(offset: Int, length: Int): BigInt = BigInt(Numeric.toBigInt(extractBytesX(offset, length)))

  def extractBytesX(offset: Int, length: Int): Array[Byte] = {
    val start = offset * 2
    val end = start + length * 2
    if (data.length < end) {
      throw new Exception("substring index out of range:[" + start + "," + end + "]")
    }
    val str = data.slice(start, end)
    Numeric.hexStringToByteArray(str)
  }

  def length(): Int = {
    data.length / 2
  }

}
