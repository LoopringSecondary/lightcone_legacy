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

import org.web3j.utils.Numeric
import com.google.protobuf.ByteString
import io.lightcone.core.Amount

object NumericConversion {

  def toBigInt(str: String): BigInt =
    try {
      BigInt(Numeric.toBigInt(str))
    } catch {
      case e: Throwable => BigInt(0)
    }

  def toAmount(str: String): Amount =
    toAmount(toBigInt(str))

  def toBigInt(amount: Amount): BigInt =
    toBigInt(amount.value)

  def toBigInt(amountOpt: Option[Amount]): BigInt =
    amountOpt match {
      case Some(amount) =>
        Numeric.toBigInt(amount.value.toByteArray)
      case _ => BigInt(0)
    }

  def toBigInt(bs: ByteString): BigInt =
    try {
      BigInt(bs.toByteArray)
    } catch {
      case e: Throwable => BigInt(0)
    }

  def toHexString(bi: BigInt): String =
    Numeric.toHexStringWithPrefix(bi.bigInteger)

  def toHexString(amount: Amount): String =
    Numeric.toHexStringWithPrefix(toBigInt(amount).bigInteger)

  def toAmount(bi: BigInt): Amount =
    Amount(ByteString.copyFrom(bi.toByteArray))
}
