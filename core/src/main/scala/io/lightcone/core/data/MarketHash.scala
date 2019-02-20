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

import io.lightcone.lib._
import org.slf4s.Logging

object MarketHash {
  def apply(marketPair: MarketPair): MarketHash = new MarketHash(marketPair)

  def hashToId(hash: String) = {
    var h = hash.toLowerCase
    h = if (h.startsWith("0x")) h.substring(2) else h
    if (h == "") 0L
    else MurmurHash64.hash(h).abs
  }
}

class MarketHash(marketPair: MarketPair) extends Object with Logging {
  import MarketHash._

  val bigIntValue = {
    try {
      (Address(marketPair.baseToken).toBigInt ^
        Address(marketPair.quoteToken).toBigInt)
    } catch {
      case e: Throwable =>
        log.error(s"unable to convert token addresses into BigInt: $marketPair")
        throw e
    }
  }

  def hashString() = s"0x${bigIntValue.toString(16)}"

  def longId() = hashToId(hashString)

  def getBytes() = bigIntValue.toByteArray

  override def toString() = hashString
}
