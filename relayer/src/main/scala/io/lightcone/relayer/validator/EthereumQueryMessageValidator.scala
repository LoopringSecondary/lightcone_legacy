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
import io.lightcone.core._
import io.lightcone.core.MetadataManager

import io.lightcone.proto._

import scala.concurrent._

// Owner: Yadong
object EthereumQueryMessageValidator {
  val name = "ethereum_query_validator"
}

final class EthereumQueryMessageValidator(
  )(
    implicit
    metadataManager: MetadataManager,
    val config: Config,
    ec: ExecutionContext)
    extends MessageValidator {

  def normalize(token: String): String = {
    if (metadataManager.hasSymbol(token)) {
      metadataManager.getTokenBySymbol(token).get.meta.address
    } else if (Address.isValid(token)) {
      Address.normalize(token)
    } else {
      throw ErrorException(
        code = ErrorCode.ERR_ETHEREUM_ILLEGAL_ADDRESS,
        message = s"unexpected token $token"
      )
    }
  }

  // Throws exception if validation fails.
  def validate = {
    case req: GetBalanceAndAllowances.Req =>
      Future {
        req
          .copy(tokens = req.tokens.map(normalize))
          .copy(address = Address.normalize(req.address))
      }

    case req: GetBalance.Req =>
      Future {
        req
          .copy(tokens = req.tokens.map(normalize))
          .copy(address = Address.normalize(req.address))
      }

    case req: GetAllowance.Req =>
      Future {
        req
          .copy(tokens = req.tokens.map(normalize))
          .copy(address = Address.normalize(req.address))
      }

    // case req: GetFilledAmount.Req =>
  }
}
