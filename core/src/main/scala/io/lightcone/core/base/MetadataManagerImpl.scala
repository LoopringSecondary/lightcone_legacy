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

final class MetadataManagerImpl @Inject()(implicit val config: Config)
    extends MetadataManager
    with Logging {

  import ErrorCode._

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
  private var marketQuoteTokens = Set.empty[String] // LRC, WETH, USDT, TUSD

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
    marketQuoteTokens = Set.empty

    markets.foreach { meta =>
      val m = MetadataManager.normalize(meta)
      marketMap += m.marketHash -> m
      marketQuoteTokens += m.quoteTokenSymbol
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

  def getMarketQuoteTokens() = marketQuoteTokens
}
