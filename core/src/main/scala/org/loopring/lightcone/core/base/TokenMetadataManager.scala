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
import org.loopring.lightcone.proto._

class TokenMetadataManager(defaultBurnRate: Double = 0.2) {

  private var addressMap = Map.empty[String, XTokenMetadata]
  private var symbolMap = Map.empty[String, XTokenMetadata]

  def reset(tokens: Seq[XTokenMetadata]) = this.synchronized {
    addressMap = Map.empty
    symbolMap = Map.empty
    tokens.foreach(addToken)
  }

  def addToken(token: XTokenMetadata) = this.synchronized {
    addressMap += token.address -> token
  }

  def hasTokenByAddress(token: String) = addressMap.contains(token)
  def getTokenByAddress(token: String) = addressMap.get(token)

  def hasTokenBySymbol(symbol: String) = symbolMap.contains(symbol)
  def getTokenBySymbbol(symbol: String) = symbolMap.get(symbol)

  def getBurnRate(token: String) =
    addressMap.get(token).map(_.burnRate).getOrElse(defaultBurnRate)
}
