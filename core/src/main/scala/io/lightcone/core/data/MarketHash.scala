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

import io.lightcone.lib.MurmurHash64

object MarketHash {
  def apply(marketPair: MarketPair): MarketHash = new MarketHash(marketPair)

  def hashToId(hash: String) = {
    var h = hash.toLowerCase
    h = if (h.startsWith("0x")) h.substring(2) else h
    if (h == "") 0L
    else MurmurHash64.hash(h).abs
  }
}

class MarketHash(marketPair: MarketPair) {
  import MarketHash._

  val bigIntValue = {
    try {
      (Address(marketPair.baseToken).toBigInt ^
        Address(marketPair.quoteToken).toBigInt)
    } catch {
      case e: Throwable => BigInt(0)
    }
  }

  def hashString() = s"0x${bigIntValue.toString(16)}"

  def longId() = hashToId(hashString)

  def getBytes() = bigIntValue.toByteArray

  override def toString() = hashString
}
