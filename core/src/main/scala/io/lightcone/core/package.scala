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

import io.lightcone.lib._
import org.web3j.utils.Numeric
import spire.math.Rational
import com.google.protobuf.ByteString

package object core {

  def createRingIdByOrderHash(
    orderhash1: String,
    orderhash2: String) = {
    val hash = NumericConversion.toBigInt(orderhash1) ^
      NumericConversion.toBigInt(orderhash2)
    Numeric.toHexString(hash.toByteArray).toLowerCase()
  }

  /// -----implicit methods -----
  implicit def byteString2BigInt(bs: ByteString): BigInt =
    NumericConversion.toBigInt(bs)

  implicit def bigInt2ByteString(b: BigInt): ByteString =
    new RichBigInt(b).toByteArray

  implicit def byteArray2ByteString(bytes: Array[Byte]) =
    ByteString.copyFrom(bytes)

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
