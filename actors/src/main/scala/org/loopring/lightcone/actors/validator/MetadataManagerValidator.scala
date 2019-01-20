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
import org.loopring.lightcone.actors.utils.MetadataRefresher
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto._

object MetadataManagerValidator {
  val name = "metadata_manager_validator"
}

final class MetadataManagerValidator()(implicit val config: Config)
    extends MessageValidator {

  def validate = {
    case req: SaveTokenMetadatas.Req =>
      if (req.tokens.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Parameter tokens could not be empty"
        )
      // address toLowerCase, symbol toUpperCase
      val tokens = req.tokens.map(MetadataRefresher.formatToken)
      req.copy(tokens = tokens)

    case req: UpdateTokenMetadata.Req =>
      if (req.token.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Parameter token could not be empty"
        )
      // address toLowerCase, symbol toUpperCase
      req.copy(token = Some(MetadataRefresher.formatToken(req.token.get)))

    case req: UpdateTokenBurnRate.Req =>
      if (req.address.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Parameter address could not be empty"
        )
      req

    case req: DisableToken.Req =>
      if (req.address.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Parameter address could not be empty"
        )
      req

    case req: SaveMarketMetadatas.Req =>
      if (req.markets.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Parameter markets could not be empty"
        )
      val markets = req.markets.map(MetadataRefresher.formatMarket)
      req.copy(markets = markets)

    case req: UpdateMarketMetadata.Req =>
      if (req.market.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Parameter market could not be empty"
        )
      req.copy(market = Some(MetadataRefresher.formatMarket(req.market.get)))

    case req: DisableMarket.Req =>
      if (req.marketHash.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Parameter marketHash could not be empty"
        )
      req

    case req: LoadTokenMetadata.Req =>
      req

    case req: LoadMarketMetadata.Req =>
      req

  }
}
