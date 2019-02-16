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

import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.relayer.data.TokenBurnRateChangedEvent._
import org.slf4s.Logging
import scala.collection.JavaConverters._

object MetadataManager {

  import ErrorCode._

  def normalize(token: TokenMetadata): TokenMetadata =
    token.copy(
      address = Address(token.address).toString.toLowerCase,
      symbol = token.symbol.toUpperCase
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

  def getBurnRate(addr: String): BurnRate

  def getTokens(): Seq[Token]

  def getMarkets(
      status: Set[MarketMetadata.Status] = Set.empty
    ): Seq[MarketMetadata]

  def getValidMarketPairs(): Map[String, MarketPair]

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

  def getMarketMetadata(marketHash: String): MarketMetadata

  def getMarketMetadata(marketPair: MarketPair): MarketMetadata =
    getMarketMetadata(MarketHash(marketPair).toString)
}

final class MetadataManagerImpl @Inject()(implicit val config: Config)
    extends MetadataManager
    with Logging {

  import ErrorCode._
  import MarketMetadata.Status._

  val loopringConfig = config.getConfig("loopring_protocol")

  val rates = loopringConfig
    .getConfigList("burn-rate-table.tiers")
    .asScala
    .map(conf => {
      val key = conf.getInt("tier")
      val ratesConfig = conf.getConfig("rates")
      val rates = ratesConfig.getInt("market") -> ratesConfig.getInt("p2p")
      key -> rates
    })
    .sortWith(_._1 < _._1)
    .head
    ._2
  val base = loopringConfig.getInt("burn-rate-table.base")

  // tokens[address, token]
  val defaultBurnRateForMarket: Double = rates._1.doubleValue() / base
  val defaultBurnRateForP2P: Double = rates._2.doubleValue() / base

  private var tokenAddressMap = Map.empty[String, Token]
  private var tokenSymbolMap = Map.empty[String, Token]
  private var marketMap = Map.empty[String, MarketMetadata]

  def reset(
      tokens: Seq[TokenMetadata],
      markets: Seq[MarketMetadata]
    ) = {
    tokenAddressMap = Map.empty
    tokenSymbolMap = Map.empty

    tokens.foreach { meta =>
      val m = MetadataManager.normalize(meta)
      val t = new Token(meta)
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

  def getMarkets(
      status: Set[MarketMetadata.Status] = Set.empty
    ): Seq[MarketMetadata] = {
    marketMap.values.filter(m => status.contains(m.status)).toSeq
  }

  def getMarketMetadata(marketHash: String): MarketMetadata =
    marketMap
      .getOrElse(
        marketHash.toLowerCase,
        throw ErrorException(
          ERR_INTERNAL_UNKNOWN,
          s"no metadata for market($marketHash)"
        )
      )

  def getValidMarketPairs =
    marketMap.values.filter { meta =>
      meta.status == ACTIVE || meta.status == READONLY
    }.map { meta =>
      meta.marketHash -> meta.marketPair.get
    }.toMap

}
