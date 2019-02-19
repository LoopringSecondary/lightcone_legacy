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

object NumericConversion {

  def toBigInt(str: String): BigInt =
    try {
      BigInt(Numeric.toBigInt(str))
    } catch {
      case e: Throwable => BigInt(0)
    }

  def toBigInt(bs: ByteString): BigInt =
    try {
      BigInt(bs.toByteArray)
    } catch {
      case e: Throwable => BigInt(0)
    }

  def toHexString(bi: BigInt) = Numeric.toHexStringWithPrefix(bi.bigInteger)

}
