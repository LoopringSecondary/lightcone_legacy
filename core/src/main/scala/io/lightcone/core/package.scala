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

import org.web3j.utils.Numeric
import spire.math.Rational
import com.google.protobuf.ByteString

package object core {

  // TODO(dongw): we need to remove this method.
  def formatHex(str: String): String = {
    if (Numeric.cleanHexPrefix(str).isEmpty) str + "0" else str
  }

  def createRingIdByOrderHash(
      orderhash1: String,
      orderhash2: String
    ) = {
    val hash = Numeric.toBigInt(orderhash1) xor
      Numeric.toBigInt(orderhash2)
    Numeric.toHexString(hash.toByteArray).toLowerCase()
  }

  /// -----implicit methods -----
  implicit def byteString2BigInt(bytes: ByteString): BigInt = {
    if (bytes.size() > 0) BigInt(bytes.toByteArray)
    else BigInt(0)
  }

  implicit def rational2BigInt(r: Rational) = r.toBigInt

  implicit def bigInt2ByteString(b: BigInt): ByteString =
    ByteString.copyFrom(b.toByteArray)

  implicit def byteArray2ByteString(bytes: Array[Byte]) =
    ByteString.copyFrom(bytes)

  implicit def byteString2HexString(bytes: ByteString): String = {
    Numeric.toHexStringWithPrefix(bytes.bigInteger)
  }

  /// -----implicit classes -----
  implicit class Rich_OrderbookSlot(raw: Orderbook.Slot)
      extends RichOrderbookSlot(raw)

  implicit class Rich_BigInt(raw: BigInt) extends RichBigInt(raw)

  implicit class Rich_ExpectedFill(raw: ExpectedMatchableFill)
      extends RichExpectedFill(raw)

  implicit class Rich_Matchable(raw: Matchable) extends RichMatchable(raw)

  implicit class Rich_MatchableRing(raw: MatchableRing)
      extends RichMatchableRing(raw)
}
