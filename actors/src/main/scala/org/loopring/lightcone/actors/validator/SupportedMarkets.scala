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

import java.math.BigInteger
import com.typesafe.config.Config
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.{ErrorException, MarketHashProvider}
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

// Owner: Hongyu
case class SupportedMarkets(config: Config) {

  private var disabledMarketsKey: Set[BigInteger] = Set.empty
  private var enabledMarketsKey: Set[BigInteger] = Set.empty
  private var readOnlyMarketsKey: Set[BigInteger] = Set.empty
  private var marketsMetadataMap = Map.empty[String, MarketMetadata]

  private def toMarketHashInBigInt(
      primary: String,
      secondary: String
    ): BigInteger =
    Numeric.toBigInt(primary) xor Numeric.toBigInt(secondary)

  def reset(metas: Seq[MarketMetadata]) = this.synchronized {
    marketsMetadataMap = Map.empty
    disabledMarketsKey = Set.empty
    enabledMarketsKey = Set.empty
    readOnlyMarketsKey = Set.empty
    metas.foreach(addMarket)
  }

  def addMarket(meta: MarketMetadata) = this.synchronized {
    marketsMetadataMap += meta.marketHash -> meta
    meta.status match {
      case MarketMetadata.Status.DISABLED =>
        disabledMarketsKey += Numeric.toBigInt(meta.marketHash)
      case MarketMetadata.Status.ENABLED =>
        enabledMarketsKey += Numeric.toBigInt(meta.marketHash)
      case MarketMetadata.Status.READONLY =>
        readOnlyMarketsKey += Numeric.toBigInt(meta.marketHash)
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
    enabledMarketsKey.contains(marketId.key)
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

  def getMarketKeys() = enabledMarketsKey
}
