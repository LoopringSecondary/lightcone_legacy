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

package io.lightcone.relayer.validator

import com.typesafe.config.Config
import io.lightcone.core.MetadataManager

import io.lightcone.core._
import io.lightcone.proto._

import scala.concurrent.{ ExecutionContext, Future }

object MetadataManagerValidator {
  val name = "metadata_manager_validator"
}

final class MetadataManagerValidator()(
  implicit val config: Config,
  ec: ExecutionContext)
  extends MessageValidator {

  import ErrorCode._

  def validate = {
    case req: SaveTokenMetadatas.Req =>
      Future {
        if (req.tokens.isEmpty)
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            "Parameter tokens could not be empty")
        // address toLowerCase, symbol toUpperCase
        val tokens = req.tokens.map(MetadataManager.normalizeToken)
        req.copy(tokens = tokens)
      }

    case req: UpdateTokenMetadata.Req =>
      Future {
        if (req.token.isEmpty)
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            "Parameter token could not be empty")
        // address toLowerCase, symbol toUpperCase
        req.copy(token = Some(MetadataManager.normalizeToken(req.token.get)))
      }

    case req: UpdateTokenBurnRate.Req =>
      Future {
        if (req.address.isEmpty)
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            "Parameter address could not be empty")
        req
      }
    case req: InvalidateToken.Req =>
      Future {
        if (req.address.isEmpty)
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            "Parameter address could not be empty")
        req
      }
    case req: SaveMarketMetadatas.Req =>
      Future {
        if (req.markets.isEmpty)
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            "Parameter markets could not be empty")
        val markets = req.markets.map(MetadataManager.normalizeMarket)
        req.copy(markets = markets)
      }

    case req: UpdateMarketMetadata.Req =>
      Future {
        if (req.market.isEmpty)
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            "Parameter market could not be empty")
        req.copy(market = Some(MetadataManager.normalizeMarket(req.market.get)))
      }

    case req: TerminateMarket.Req =>
      Future {
        if (req.marketHash.isEmpty)
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            "Parameter marketHash could not be empty")
        req
      }

    case req: LoadTokenMetadata.Req =>
      Future.successful(req)

    case req: LoadMarketMetadata.Req =>
      Future.successful(req)

  }
}
