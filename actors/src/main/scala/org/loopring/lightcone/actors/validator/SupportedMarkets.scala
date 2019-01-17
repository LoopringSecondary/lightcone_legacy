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

package org.loopring.lightcone.actors.validator

import com.typesafe.config.Config
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.{ErrorException, MarketHashProvider}
import org.loopring.lightcone.proto._

// Owner: Hongyu
case class SupportedMarkets(config: Config) {

  // marketHash -> MarketId
  private var disabledMarkets: Map[String, MarketId] = Map.empty
  private var enabledMarkets: Map[String, MarketId] = Map.empty
  private var readOnlyMarkets: Map[String, MarketId] = Map.empty
  private var metadatas = Map.empty[String, MarketMetadata]

  private def toMarketHashInBigInt(
      primary: String,
      secondary: String
    ): String = MarketHashProvider.convert2Hex(primary, secondary)

  def reset(metas: Seq[MarketMetadata]) = this.synchronized {
    metadatas = Map.empty
    disabledMarkets = Map.empty
    enabledMarkets = Map.empty
    readOnlyMarkets = Map.empty
    metas.foreach(addMarket)
  }

  def addMarket(meta: MarketMetadata) = this.synchronized {
    metadatas += meta.marketHash -> meta
    meta.status match {
      case MarketMetadata.Status.DISABLED =>
        disabledMarkets += meta.marketHash -> meta.marketId.get
      case MarketMetadata.Status.ENABLED =>
        enabledMarkets += meta.marketHash -> meta.marketId.get
      case MarketMetadata.Status.READONLY =>
        readOnlyMarkets += meta.marketHash -> meta.marketId.get
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

  def contains(marketId: MarketId) = {
    enabledMarkets.contains(marketId.key.toString(16))
  }

  def assertmarketIdIsValid(marketIdOpt: Option[MarketId]): Option[MarketId] = {
    marketIdOpt match {
      case None =>
        throw ErrorException(ErrorCode.ERR_INVALID_MARKET)
      case Some(marketId) =>
        val marketIdRes = assertmarketIdIsValid(marketId)
        Some(marketIdRes)
    }
  }

  def assertmarketIdIsValid(marketId: MarketId): MarketId = {
    if (!contains(marketId))
      throw ErrorException(
        ErrorCode.ERR_INVALID_MARKET,
        s"invalid market: ${marketId}"
      )
    marketId.copy(
      primary = Address(marketId.primary).toString,
      secondary = Address(marketId.secondary).toString
    )
  }

  def getByMarketHash(marketHash: String): MarketMetadata =
    metadatas(marketHash)

  def getAvailableMarkets() = enabledMarkets

  def getAvaliableMarketKeys() = enabledMarkets.keySet
}
