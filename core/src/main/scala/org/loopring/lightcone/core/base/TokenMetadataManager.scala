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

import org.loopring.lightcone.core.data._
import org.loopring.lightcone.proto.core._

class TokenMetadataManager(defaultBurnRate: Double = 0.2) {

  private var tokens = Map.empty[TokenHash, XTokenMetadata]

  private var decimalsMap = Map.empty[TokenHash, Int]
  private var priceMap = Map.empty[TokenHash, Double]
  private var burnRateMap = Map.empty[TokenHash, Double]

  def addToken(token: XTokenMetadata) {
    tokens += token.hash -> token
  }

  def hasToken(token: TokenHash) = tokens.contains(token)

  def getToken(token: TokenHash) = tokens.get(token)

  def updatePrices(priceMap: Map[TokenHash, Double]) {
    tokens = tokens.map {
      case (address, token) ⇒ priceMap.get(address) match {
        case Some(price) ⇒ (address, token.copy(currentPrice = price))
        case None        ⇒ (address, token)
      }
    }
  }

  def getBurnRate(token: TokenHash) =
    tokens.get(token).map(_.burnRate).getOrElse(defaultBurnRate)

  def updateBurnRate(token: TokenHash, rate: Double) =
    tokens.get(token) match {
      case None ⇒
      case Some(meta) ⇒
        tokens += token → meta.copy(burnRate = rate)
    }
}

