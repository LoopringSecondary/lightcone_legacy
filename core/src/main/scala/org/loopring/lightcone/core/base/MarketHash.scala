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
  def apply(marketPair: MarketPair): MarketHash = new MarketHash(marketPair)
}

class MarketHash(marketPair: MarketPair) {
  import MarketHash._

  val hashString = {
    (Address(marketPair.baseToken).toBigInt ^
      Address(marketPair.quoteToken).toBigInt).toString(16)
  }

  def longId() = MurmurHash64.hash(hashString)

  def getBytes() = hashString.getBytes

  override def toString() = hashString
}
