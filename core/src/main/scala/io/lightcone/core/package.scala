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
import com.google.protobuf.ByteString

package object core {

  def createRingIdByOrderHash(
      orderhash1: String,
      orderhash2: String
    ) = {
    val hash = NumericConversion.toBigInt(orderhash1) ^
      NumericConversion.toBigInt(orderhash2)
    Numeric.toHexString(hash.toByteArray).toLowerCase()
  }

  /// -----implicit methods -----
  @inline
  implicit def byteString2BigInt(bs: ByteString): BigInt =
    NumericConversion.toBigInt(bs)

  @inline
  implicit def bigInt2ByteString(b: BigInt): ByteString =
    new RichBigInt(b).toByteString

  @inline
  implicit def amount2BigInt(amount: Amount): BigInt =
    NumericConversion.toBigInt(amount)

  @inline
  implicit def bigInt2Amount(b: BigInt): Amount =
    NumericConversion.toAmount(b)

  @inline
  implicit def amountOpt2BigInt(amountOpt: Option[Amount]): BigInt =
    NumericConversion.toBigInt(amountOpt)

  @inline
  implicit def bigInt2AmountOpt(b: BigInt): Option[Amount] =
    Some(NumericConversion.toAmount(b))

  @inline
  implicit def byteArray2ByteString(bytes: Array[Byte]) =
    ByteString.copyFrom(bytes)

  @inline
  implicit def byteString2HexString(bytes: ByteString) =
    Numeric.toHexStringWithPrefix(bytes.bigInteger)

  /// -----implicit classes -----
  @inline
  implicit class Rich_OrderbookSlot(raw: Orderbook.Slot)
      extends RichOrderbookSlot(raw)

  @inline
  implicit class Rich_BigInt(raw: BigInt) extends RichBigInt(raw)

  @inline
  implicit class Rich_ExpectedFill(raw: ExpectedMatchableFill)
      extends RichExpectedFill(raw)

  @inline
  implicit class Rich_Matchable(raw: Matchable) extends RichMatchable(raw)

  @inline
  implicit class Rich_MatchableRing(raw: MatchableRing)
      extends RichMatchableRing(raw)
}
