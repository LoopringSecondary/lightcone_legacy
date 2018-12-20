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
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

import scala.collection.JavaConverters._

case class SupportedMarkets(config: Config) {

  private val markets = config
    .getObjectList("markets")
    .asScala
    .map { item =>
      val c = item.toConfig
      Numeric.toBigInt(c.getString("priamry")) xor
        Numeric.toBigInt(c.getString("secondary"))
    }
    .toSet

  private def contains(marketId: XMarketId) = {
    val eig = Numeric.toBigInt(marketId.primary) xor
      Numeric.toBigInt(marketId.secondary)
    markets.contains(eig)
  }

  def assertmarketIdIsValid(
      marketIdOpt: Option[XMarketId]
    ): Option[XMarketId] = {
    marketIdOpt match {
      case None =>
        throw ErrorException(XErrorCode.ERR_INVALID_MARKET)
      case Some(marketId) =>
        val marketIdRes = assertmarketIdIsValid(marketId)
        Some(marketIdRes)
    }
  }

  def assertmarketIdIsValid(marketId: XMarketId): XMarketId = {
    if (!contains(marketId))
      throw ErrorException(
        XErrorCode.ERR_INVALID_MARKET,
        s"invalid market: ${marketId}"
      )
    marketId.copy(
      primary = marketId.primary.toLowerCase(),
      secondary = marketId.secondary.toLowerCase()
    )
  }
}
