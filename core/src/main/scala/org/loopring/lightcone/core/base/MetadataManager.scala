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

package org.loopring.lightcone.core

import com.google.inject.Inject
import com.typesafe.config.Config
import org.loopring.lightcone.lib.ErrorException

import org.loopring.lightcone.proto._

import org.loopring.lightcone.proto.TokenBurnRateChangedEvent._
import org.slf4s.Logging
import scala.collection.JavaConverters._

import ErrorCode._

object MetadataManager {

  def normalizeToken(token: TokenMetadata): TokenMetadata =
    token.copy(
      address = token.address.toLowerCase(),
      symbol = token.symbol.toUpperCase()
    )

  def normalizeMarket(market: MarketMetadata): MarketMetadata = {
    val marketPair = market.marketPair.getOrElse(
      throw ErrorException(ERR_INVALID_ARGUMENT, "marketPair is empty")
    )

    if (MarketHash(marketPair).toString != market.marketHash.toLowerCase())
      throw ErrorException(
        ERR_INVALID_ARGUMENT,
        s"marketPair:$marketPair mismatch marketHash:${market.marketHash}"
      )

    market.copy(
      baseTokenSymbol = market.baseTokenSymbol.toUpperCase(),
      quoteTokenSymbol = market.quoteTokenSymbol.toUpperCase(),
      marketPair = Some(
        MarketPair(
          marketPair.baseToken.toLowerCase(),
          marketPair.quoteToken.toLowerCase()
        )
      ),
      marketHash = market.marketHash.toLowerCase()
    )
  }
}

final class MetadataManager @Inject()(implicit val config: Config)
    extends Logging {

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
  private var addressMap = Map.empty[String, Token]
  private var symbolMap = Map.empty[String, Token]

  // markets[marketHash, marketPair]
  private var terminatedMarkets: Map[String, MarketPair] = Map.empty
  private var activeMarkets: Map[String, MarketPair] = Map.empty
  private var readOnlyMarkets: Map[String, MarketPair] = Map.empty

  private var marketMetadatasMap = Map.empty[String, MarketMetadata]

  def reset(
      tokens: Seq[TokenMetadata],
      markets: Seq[MarketMetadata]
    ) = this.synchronized {
    addressMap = Map.empty
    tokens.foreach(addToken)

    terminatedMarkets = Map.empty
    activeMarkets = Map.empty
    readOnlyMarkets = Map.empty
    marketMetadatasMap = Map.empty
    markets.foreach(addMarket)
  }

  private def addToken(meta: TokenMetadata) = this.synchronized {
    val m = MetadataManager.normalizeToken(meta)
    val token = new Token(m)
    addressMap += m.address -> token
    symbolMap += m.symbol -> token
    this
  }

  private def addTokens(meta: Seq[TokenMetadata]) = {
    meta.foreach(addToken)
    this
  }

  def hasToken(addr: String) = addressMap.contains(addr.toLowerCase())

  def hasSymbol(symbol: String) = symbolMap.contains(symbol.toUpperCase())

  def getToken(addr: String) = {
    // assert(hasToken(addr.toLowerCase()), s"token no found for address $addr")
    addressMap.get(addr.toLowerCase())
  }

  def getTokenBySymbol(symbol: String) = {
    // assert(hasSymbol(symbol.toLowerCase()), s"token no found for symbol $symbol")
    symbolMap.get(symbol.toUpperCase())
  }

  def getBurnRate(addr: String) =
    addressMap
      .get(addr.toLowerCase())
      .map(m => BurnRate(m.meta.burnRateForMarket, m.meta.burnRateForP2P))
      .getOrElse(BurnRate(defaultBurnRateForMarket, defaultBurnRateForP2P))

  def getTokens = addressMap.values.toSeq

  private def addMarket(meta: MarketMetadata) = this.synchronized {
    val m = MetadataManager.normalizeMarket(meta)
    marketMetadatasMap += m.marketHash -> m
    val itemMap = m.marketHash -> m.marketPair.get

    m.status match {
      case MarketMetadata.Status.TERMINATED =>
        terminatedMarkets += itemMap
      case MarketMetadata.Status.ACTIVE =>
        activeMarkets += itemMap
      case MarketMetadata.Status.READONLY =>
        readOnlyMarkets += itemMap
      case m =>
        throw ErrorException(
          ERR_INTERNAL_UNKNOWN,
          s"Unhandled market metadata status:$m"
        )
    }
    this
  }

  def addMarkets(meta: Seq[MarketMetadata]) = {
    meta.foreach(addMarket)
    this
  }

  def getMarkets(
      status: Set[MarketMetadata.Status] = Set.empty
    ): Seq[MarketMetadata] = {
    marketMetadatasMap.values.filter(m => status.contains(m.status)).toSeq
  }

  def getMarketMetadata(marketHash: String): MarketMetadata =
    marketMetadatasMap
      .getOrElse(
        marketHash.toLowerCase,
        throw ErrorException(
          ERR_INTERNAL_UNKNOWN,
          s"no metadata for market($marketHash)"
        )
      )

  def getMarketMetadata(marketPair: MarketPair): MarketMetadata =
    getMarketMetadata(MarketHash(marketPair).toString)

  def assertMarketPairIsActiveOrReadOnly(
      marketPairOpt: Option[MarketPair]
    ): Boolean = {
    marketPairOpt match {
      case None =>
        throw ErrorException(ERR_INVALID_MARKET)
      case Some(marketPair) =>
        if (!isMarketActiveOrReadOnly(MarketHash(marketPair).toString))
          throw ErrorException(
            ErrorCode.ERR_INVALID_MARKET,
            s"invalid market: $marketPairOpt"
          )
        true
    }
  }

  def assertMarketPairIsActiveOrReadOnly(marketPair: MarketPair): Boolean = {
    if (!isMarketActiveOrReadOnly(marketPair))
      throw ErrorException(ERR_INVALID_MARKET, s"invalid market: $marketPair")
    true
  }

  def assertMarketPairIsActive(marketPair: MarketPair): Boolean = {
    if (!activeMarkets.contains(MarketHash(marketPair).toString))
      throw ErrorException(
        ErrorCode.ERR_INVALID_MARKET,
        s"marketPair:$marketPair has been terminated"
      )
    true
  }

  // check market is valid (has metadata config)
  def isMarketActiveOrReadOnly(marketHash: String): Boolean =
    getValidMarketPairs.contains(marketHash)

  def isMarketActiveOrReadOnly(marketPair: MarketPair): Boolean =
    isMarketActiveOrReadOnly(MarketHash(marketPair).toString)

  def getValidMarketPairs = activeMarkets ++ readOnlyMarkets

}
