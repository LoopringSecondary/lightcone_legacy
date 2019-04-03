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

import org.slf4s.Logging
import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConverters._
import scala.collection.mutable

final class MetadataManagerImpl(
    val defaultBurnRateForMarket: Double,
    val defaultBurnRateForP2P: Double)
    extends MetadataManager
    with Logging {

  import ErrorCode._

  private var tokenAddressMap = mutable.Map.empty[String, Token]
  private var tokenSymbolMap = mutable.Map.empty[String, Token]
  private var marketMap = mutable.Map.empty[String, Market]

  def reset(
      tokens: Seq[Token],
      markets: Seq[Market]
    ) = {
    log.debug(
      s"MetadataManagerImpl -- reset -- tokens:${tokens.mkString}, markets:${markets.mkString}"
    )
    val tokenAddressMapTmp: mutable.Map[String, Token] =
      new ConcurrentHashMap[String, Token]().asScala
    val tokenSymbolMapTmp: mutable.Map[String, Token] =
      new ConcurrentHashMap[String, Token]().asScala
    val marketMapTmp: mutable.Map[String, Market] =
      new ConcurrentHashMap[String, Market]().asScala

    tokens.foreach { token =>
      tokenAddressMapTmp.put(token.getMetadata.address, token)
      tokenSymbolMapTmp.put(token.getMetadata.symbol, token)
    }

    markets.foreach { market =>
      marketMapTmp.put(market.getMetadata.marketHash, market)
    }

    tokenAddressMap = tokenAddressMapTmp
    tokenSymbolMap = tokenSymbolMapTmp
    marketMap = marketMapTmp
  }

  def isMarketStatus(
      marketHash: String,
      statuses: MarketMetadata.Status*
    ): Boolean =
    marketMap
      .get(marketHash)
      .exists(m => statuses.contains(m.getMetadata.status))

  def getTokenWithAddress(addr: String): Option[Token] = {
    tokenAddressMap.get(addr.toLowerCase())
  }

  def getTokenWithSymbol(symbol: String) = {
    tokenSymbolMap.get(symbol.toUpperCase())
  }

  def getBurnRate(addr: String) =
    tokenAddressMap
      .get(addr.toLowerCase())
      .map(_.getMetadata.getBurnRate)
      .getOrElse(BurnRate(defaultBurnRateForMarket, defaultBurnRateForP2P))

  def getTokens = tokenAddressMap.values.toSeq

  def getMarket(marketHash: String): Market =
    marketMap
      .getOrElse(
        marketHash.toLowerCase,
        throw ErrorException(
          ERR_INTERNAL_UNKNOWN,
          s"no metadata for market($marketHash)"
        )
      )

  def getMarkets(): Seq[Market] = marketMap.values.toSeq

  def getMarkets(status: MarketMetadata.Status*): Seq[Market] = {
    marketMap.values.filter(m => status.contains(m.metadata.get.status)).toSeq
  }

}
