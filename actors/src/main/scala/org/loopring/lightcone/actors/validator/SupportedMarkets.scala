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
import org.web3j.utils.Numeric

import scala.collection.JavaConverters._

// Owner: Hongyu
case class SupportedMarkets(config: Config) {

  private var disabledMarketsKey:Set[BigInt] = Set.empty
  private var enabledMarketsKey = config
    .getObjectList("markets")
    .asScala
    .map { item =>
      val c = item.toConfig
      toMarketHashInBigInt(c.getString("primary"), c.getString("secondary"))
    }
    .toSet
  private var readOnlyMarketsKey = Set.empty

  private var marketsMetadataMap = Map.empty[String, MarketMetadata]

  private def toMarketHashInBigInt(primary:String, secondary: String): BigInt =
    Numeric.toBigInt(primary) xor Numeric.toBigInt(secondary)

  def reset(metas: Seq[MarketMetadata]) = this.synchronized {
    marketsMetadataMap = Map.empty
    disabledMarketsKey = metas.filter(_.status == MarketMetadata.Status.DISABLED).map(m=> toMarketHashInBigInt()).toSet

    metas.foreach(addMarket)
  }

  def addMarket(meta: MarketMetadata) = this.synchronized {
    marketsMetadataMap += meta.marketHash -> meta
    println(1111111, supportedMarketsKeys)
    supportedMarketsKeys = supportedMarketsKeys + Numeric.toBigInt(meta.marketHash)
    println(2222222, supportedMarketsKeys)
    this
  }

  def addMarkets(meta: Seq[MarketMetadata]) = {
    meta.foreach(addMarket)
    this
  }

  def contains(marketId: MarketId) = {
    supportedMarketsKeys.contains(marketId.key)
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

  def getMarketKeys() = supportedMarketsKeys
}
