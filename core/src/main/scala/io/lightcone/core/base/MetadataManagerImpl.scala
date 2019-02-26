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

import io.lightcone.ethereum.event.TokenBurnRateChangedEvent._
import org.slf4s.Logging

final class MetadataManagerImpl(
    val defaultBurnRateForMarket: Double,
    val defaultBurnRateForP2P: Double)
    extends MetadataManager
    with Logging {

  import ErrorCode._

  private var tokenAddressMap = Map.empty[String, Token]
  private var tokenSymbolMap = Map.empty[String, Token]
  private var marketMap = Map.empty[String, MarketMetadata]

  def reset(
      tokens: Seq[TokenMetadata],
      tickerMap: Map[String, Double],
      markets: Seq[MarketMetadata]
    ) = {
    tokenAddressMap = Map.empty
    tokenSymbolMap = Map.empty

    tokens.foreach { meta =>
      val m = MetadataManager.normalize(meta)
      val t = new Token(meta, tickerMap.getOrElse(m.symbol, 0))
      tokenAddressMap += m.address -> t
      tokenSymbolMap += m.symbol -> t
    }
    marketMap = Map.empty

    markets.foreach { meta =>
      val m = MetadataManager.normalize(meta)
      marketMap += m.marketHash -> m
    }
  }

  def isMarketStatus(
      marketHash: String,
      statuses: MarketMetadata.Status*
    ): Boolean =
    marketMap
      .get(marketHash)
      .map(m => statuses.contains(m.status))
      .getOrElse(false)

  def getTokenWithAddress(addr: String): Option[Token] = {
    tokenAddressMap.get(addr.toLowerCase())
  }

  def getTokenWithSymbol(symbol: String) = {
    tokenSymbolMap.get(symbol.toUpperCase())
  }

  def getBurnRate(addr: String) =
    tokenAddressMap
      .get(addr.toLowerCase())
      .map(m => BurnRate(m.meta.burnRateForMarket, m.meta.burnRateForP2P))
      .getOrElse(BurnRate(defaultBurnRateForMarket, defaultBurnRateForP2P))

  def getTokens = tokenAddressMap.values.toSeq

  def getMarket(marketHash: String): MarketMetadata =
    marketMap
      .getOrElse(
        marketHash.toLowerCase,
        throw ErrorException(
          ERR_INTERNAL_UNKNOWN,
          s"no metadata for market($marketHash)"
        )
      )

  def getMarkets(): Seq[MarketMetadata] = marketMap.values.toSeq

  def getMarkets(status: MarketMetadata.Status*): Seq[MarketMetadata] = {
    marketMap.values.filter(m => status.contains(m.status)).toSeq
  }

}
