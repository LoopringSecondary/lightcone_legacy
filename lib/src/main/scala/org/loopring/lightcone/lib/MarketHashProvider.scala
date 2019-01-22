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

package org.loopring.lightcone.lib

import java.math.BigInteger

import org.loopring.lightcone.proto.MarketId

object MarketHashProvider {

  private def removePrefix(address: String): String = {
    if (address.startsWith("0x")) {
      address.substring(2).trim
    } else {
      address.trim
    }
  }

  implicit class RichMarketId(marketId: MarketId) {

    def key(): BigInt = {
      BigInt(removePrefix(marketId.primary), 16) ^ BigInt(
        removePrefix(marketId.secondary),
        16
      )
    }

    def keyHex(): String = {
      ("0x" + marketId.key().toString(16)).toLowerCase()
    }

    def entityId(): String = {
      Math.abs(marketId.key().hashCode).toString
    }

    def toLowerCase(): MarketId = {
      marketId.copy(
        primary = marketId.primary.toLowerCase(),
        secondary = marketId.secondary.toLowerCase()
      )
    }
  }
}
