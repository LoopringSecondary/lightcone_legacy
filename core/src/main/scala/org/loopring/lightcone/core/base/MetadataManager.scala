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

import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.TokenBurnRateChangedEvent._
import org.slf4s.Logging

final class MetadataManager() extends Logging {

  // tokens[address, token]
  val defaultBurnRateForMarket: Double = 0.2
  val defaultBurnRateForP2P: Double = 0.2
  private var addressMap: Map[String, Token] = Map.empty
  private var symbolMap: Map[String, Token] = Map.empty

  // markets[marketKey, marketId]
  private var disabledMarkets: Map[String, MarketId] = Map.empty
  private var enabledMarkets: Map[String, MarketId] = Map.empty
  private var readOnlyMarkets: Map[String, MarketId] = Map.empty

  private var marketMetadatasMap = Map.empty[String, MarketMetadata]

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
  }

  def addToken(meta: TokenMetadata) = this.synchronized {
    val m = meta.copy(
      address = meta.address.toLowerCase(),
      symbol = meta.symbol.toUpperCase()
    )
    addressMap += m.address -> new Token(m)
    symbolMap += m.symbol -> new Token(m)
    this
  }

  def addTokens(meta: Seq[TokenMetadata]) = {
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

  def addMarket(meta: MarketMetadata) = this.synchronized {
    val marketId = meta.marketId.getOrElse(
      throw ErrorException(ErrorCode.ERR_INVALID_ARGUMENT, "marketId is empty")
    )
    if (MarketKey(marketId).toHexString != meta.marketHash.toLowerCase())
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        s"marketId:$marketId mismatch marketHash:${meta.marketHash}"
      )
    val m = meta.copy(
      primaryTokenSymbol = meta.primaryTokenSymbol.toUpperCase(),
      secondaryTokenSymbol = meta.secondaryTokenSymbol.toUpperCase(),
      marketId = Some(
        MarketId(
          primary = marketId.primary.toLowerCase(),
          secondary = marketId.secondary.toLowerCase()
        )
      ),
      marketHash = meta.marketHash.toLowerCase()
    )
    marketMetadatasMap += meta.marketHash.toLowerCase() -> meta
    val itemMap = meta.marketHash.toLowerCase() -> meta.marketId.get
    meta.status match {
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

  def addMarkets(meta: Seq[MarketMetadata]) = {
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

  def getMarketMetadata(marketId: MarketId): Option[MarketMetadata] =
    getMarketMetadata(MarketKey(marketId).toString)

  def assertMarketIdIsValid(marketIdOpt: Option[MarketId]): Option[MarketId] = {
    marketIdOpt match {
      case None =>
        throw ErrorException(ErrorCode.ERR_INVALID_MARKET)
      case Some(marketId) =>
        val marketIdRes = assertMarketIdIsValid(marketId)
        Some(marketIdRes)
    }
  }

  def assertMarketIdIsValid(marketId: MarketId): MarketId = {
    if (!isValidMarket(marketId))
      throw ErrorException(
        ErrorCode.ERR_INVALID_MARKET,
        s"invalid market: ${marketId}"
      )
    marketId
  }

  // check market is valid (has metadata config)
  def isValidMarket(marketKey: String): Boolean =
    marketMetadatasMap.contains(marketKey.toLowerCase())

  def isValidMarket(marketId: MarketId): Boolean =
    isValidMarket(MarketKey(marketId).toString)

  // check market is at enabled status
  def isEnabledMarket(marketKey: String): Boolean =
    enabledMarkets.contains(marketKey.toLowerCase())

  def isEnabledMarket(marketId: MarketId): Boolean =
    isEnabledMarket(MarketKey(marketId).toString)

  def getValidMarketKeys = marketMetadatasMap.keySet

  def getValidMarketIds = enabledMarkets ++ readOnlyMarkets ++ disabledMarkets

  def getEnabledMarketIds = enabledMarkets

  def getDisabledMarketIds = disabledMarkets
}
