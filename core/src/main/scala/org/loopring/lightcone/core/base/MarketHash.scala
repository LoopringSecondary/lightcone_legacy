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

package org.loopring.lightcone.core.base

import org.loopring.lightcone.proto.MarketPair
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.MurmurHash64

object MarketHash {

  def apply(marketPair: MarketPair): MarketHash =
    new MarketHash(marketPair)

  def apply(
      baseToken: String,
      quoteToken: String
    ): MarketHash =
    new MarketHash(MarketPair(baseToken, quoteToken))

  def hashStringToLongId(hashString: String) =
    if (hashString == "0x0") 0L
    else Math.abs(MurmurHash64.hash(hashString))
}

class MarketHash(marketPair: MarketPair) {
  import MarketHash._

  val bigIntValue = try {
    Address(marketPair.baseToken).toBigInt ^
      Address(marketPair.quoteToken).toBigInt
  } catch {
    case _: Throwable => BigInt(0)
  }

  def hashString() = s"0x${bigIntValue.toString(16)}"

  def longId() = hashStringToLongId(hashString)

  def getBytes() = bigIntValue.toByteArray

  override def toString() = hashString
}
