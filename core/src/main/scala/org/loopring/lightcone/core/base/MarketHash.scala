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

object MarketHash {
  def apply(marketPair: MarketPair): MarketHash = new MarketHash(marketPair)

  def apply(
      baseToken: String,
      quoteToken: String
    ): MarketHash = apply(MarketPair(baseToken, quoteToken))

  def removePrefix(address: String): String = {
    if (address.startsWith("0x")) address.substring(2).trim
    else address.trim
  }
}

class MarketHash(marketPair: MarketPair) {
  import MarketHash._

  val value = BigInt(removePrefix(marketPair.quoteToken), 16) ^
    BigInt(removePrefix(marketPair.baseToken), 16)

  override def toString = s"0x${value.toString(16).toLowerCase}"
}
