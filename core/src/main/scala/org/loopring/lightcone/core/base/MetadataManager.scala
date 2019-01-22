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

import com.google.inject.Inject
import com.typesafe.config.Config
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.lib.MarketHashProvider._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.TokenBurnRateChangedEvent._
import org.slf4s.Logging
import scala.collection.JavaConverters._

object MetadataManager {

  def normalizeToken(token: TokenMetadata): TokenMetadata =
    token.copy(
      address = token.address.toLowerCase(),
      symbol = token.symbol.toUpperCase()
    )

  def normalizeMarket(market: MarketMetadata): MarketMetadata = {
    val marketId = market.marketId.getOrElse(
      throw ErrorException(ErrorCode.ERR_INVALID_ARGUMENT, "marketId is empty")
    )
    if (marketId.keyHex() != market.marketHash.toLowerCase())
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        s"marketId:$marketId mismatch marketHash:${market.marketHash}"
      )
    market.copy(
      primaryTokenSymbol = market.primaryTokenSymbol.toUpperCase(),
      secondaryTokenSymbol = market.secondaryTokenSymbol.toUpperCase(),
      marketId = Some(
        MarketId(
          primary = marketId.primary.toLowerCase(),
          secondary = marketId.secondary.toLowerCase()
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

  // markets[marketKey, marketId]
  private var disabledMarkets: Map[String, MarketId] = Map.empty
  private var enabledMarkets: Map[String, MarketId] = Map.empty
  private var readOnlyMarkets: Map[String, MarketId] = Map.empty

  private var marketMetadatasMap = Map.empty[String, MarketMetadata]

  var marketSubscribees = Set.empty[(MarketMetadata) => Unit]
  var tokenSubscribees = Set.empty[(TokenMetadata) => Unit]

  def reset(
      tokens: Seq[TokenMetadata],
      markets: Seq[MarketMetadata]
    ) = this.synchronized {
    addressMap = Map.empty
    tokens.foreach(addToken)

    disabledMarkets = Map.empty
    enabledMarkets = Map.empty
    readOnlyMarkets = Map.empty
    marketMetadatasMap = Map.empty
    markets.foreach(addMarket)

    //subscribe
    tokens.foreach { t =>
      tokenSubscribees foreach (_(t))
    }
    markets.foreach { m =>
      marketSubscribees foreach (_(m))
    }
  }

  //todo: token为disable时，是否应该把相应的市场都删除
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
    val itemMap = m.marketHash -> m.marketId.get
    m.status match {
      case MarketMetadata.Status.DISABLED =>
        disabledMarkets += itemMap
      case MarketMetadata.Status.ENABLED =>
        enabledMarkets += itemMap
      case MarketMetadata.Status.READONLY =>
        readOnlyMarkets += itemMap
      case m =>
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"Unhandled market metadata status:$m"
        )
    }
    this
  }

  private def addMarkets(meta: Seq[MarketMetadata]) = {
    meta.foreach(addMarket)
    this
  }

  def getMarkets(
      status: Set[MarketMetadata.Status] = Set.empty
    ): Seq[MarketMetadata] = {
    marketMetadatasMap.values.filter(m => status.contains(m.status)).toSeq
  }

  def getMarketMetadata(marketKey: String): Option[MarketMetadata] =
    marketMetadatasMap.get(marketKey.toLowerCase())

  def assertMarketIdIsValid(marketIdOpt: Option[MarketId]): Boolean = {
    marketIdOpt match {
      case None =>
        throw ErrorException(ErrorCode.ERR_INVALID_MARKET)
      case Some(marketId) =>
        if (!isValidMarket(marketId.keyHex()))
          throw ErrorException(
            ErrorCode.ERR_INVALID_MARKET,
            s"invalid market: $marketIdOpt"
          )
        true
    }
  }

  // check market is valid (has metadata config)
  def isValidMarket(marketKey: String): Boolean =
    getValidMarketIds.contains(marketKey.toLowerCase())

  // check market is at enabled status
  def isEnabledMarket(marketKey: String): Boolean =
    enabledMarkets.contains(marketKey.toLowerCase())

  def isEnabledMarket(marketId: MarketId): Boolean =
    isEnabledMarket(marketId.keyHex())

  def getValidMarketIds = enabledMarkets ++ readOnlyMarkets

  def getEnabledMarketIds = enabledMarkets

  def getDisabledMarketIds = disabledMarkets

  def getReadOnlyMarketIds = readOnlyMarkets

  def subscribToken(subFun: (TokenMetadata) => Unit) = {
    tokenSubscribees = tokenSubscribees + subFun
  }

  def subscribMarket(subFun: (MarketMetadata) => Unit) = {
    marketSubscribees = marketSubscribees + subFun
  }

}
