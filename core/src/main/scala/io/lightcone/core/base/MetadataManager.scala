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
import io.lightcone.relayer.data.TokenBurnRateChangedEvent._

object MetadataManager {

  import ErrorCode._

  def normalize(token: TokenMetadata): TokenMetadata =
    token.copy(
      address = Address(token.address).toString.toLowerCase,
      symbol = token.symbol.toUpperCase,
      slug = token.slug.toLowerCase
    )

  def normalize(market: MarketMetadata): MarketMetadata = {
    val marketPair = market.marketPair.getOrElse(
      throw ErrorException(
        ERR_INVALID_ARGUMENT,
        "marketPair is missing from MetadataMetadata"
      )
    )

    if (MarketHash(marketPair).toString != market.marketHash) {
      throw ErrorException(
        ERR_INVALID_ARGUMENT,
        s"market hash do not match :$marketPair vs ${market.marketHash}"
      )
    }

    market.copy(
      baseTokenSymbol = market.baseTokenSymbol.toUpperCase,
      quoteTokenSymbol = market.quoteTokenSymbol.toUpperCase,
      marketPair = Some(
        MarketPair(
          Address(marketPair.baseToken).toString.toLowerCase,
          Address(marketPair.quoteToken).toString.toLowerCase
        )
      ),
      marketHash = market.marketHash
    )
  }
}

trait MetadataManager {

  def reset(
      tokens: Seq[TokenMetadata],
      markets: Seq[MarketMetadata]
    ): Unit

  def getTokenWithAddress(addr: String): Option[Token]
  def getTokenWithSymbol(symbol: String): Option[Token]

  def getTokens(): Seq[Token]

  def getMarket(marketHash: String): MarketMetadata

  def getMarket(marketPair: MarketPair): MarketMetadata =
    getMarket(MarketHash(marketPair).toString)

  def getMarkets(): Seq[MarketMetadata]

  def getMarkets(status: MarketMetadata.Status*): Seq[MarketMetadata]

  def getBurnRate(addr: String): BurnRate

  def isMarketStatus(
      marketHash: String,
      statuses: MarketMetadata.Status*
    ): Boolean

  def isMarketStatus(
      marketPair: MarketPair,
      statuses: MarketMetadata.Status*
    ): Boolean = isMarketStatus(MarketHash(marketPair).toString, statuses: _*)

  def assertMarketStatus(
      marketHash: String,
      statuses: MarketMetadata.Status*
    ): Unit = if (!isMarketStatus(marketHash, statuses: _*)) {
    throw ErrorException(
      ErrorCode.ERR_INVALID_MARKET,
      s"market status is not one of : $statuses"
    )
  }

  def assertMarketStatus(
      marketPair: MarketPair,
      statuses: MarketMetadata.Status*
    ): Unit = assertMarketStatus(MarketHash(marketPair).toString, statuses: _*)

  def getSupportMarketSymbols(): Set[String]
}
