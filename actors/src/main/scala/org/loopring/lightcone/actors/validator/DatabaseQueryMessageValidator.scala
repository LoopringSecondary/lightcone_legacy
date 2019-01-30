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
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto._

import scala.concurrent._

// Owner: Yongfeng
object DatabaseQueryMessageValidator {
  val name = "database_query_validator"
}

final class DatabaseQueryMessageValidator(
  )(
    implicit
    val config: Config,
    ec: ExecutionContext)
    extends MessageValidator {

  val defaultItemsPerPage =
    config.getInt("default-items-per-page")
  val maxItemsPerPage = config.getInt("max-items-per-page")

  def validate = {
    case req: GetOrdersForUser.Req =>
      Future {
        if (req.owner.isEmpty)
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            "Parameter owner could not be empty"
          )
        val newReq = req.skip match {
          case Some(s) if s.size > maxItemsPerPage =>
            throw ErrorException(
              ErrorCode.ERR_INVALID_ARGUMENT,
              s"Parameter size of paging is larger than $maxItemsPerPage"
            )

          case Some(s) if s.skip < 0 =>
            throw ErrorException(
              ErrorCode.ERR_INVALID_ARGUMENT,
              s"Invalid parameter skip of paging:${s.skip}"
            )

          case Some(_) => req

          case None =>
            req.copy(skip = Some(Paging(size = defaultItemsPerPage)))
        }
        val market =
          if (req.market.isPair)
            GetOrdersForUser.Req.Market.Pair(
              req.getPair.copy(
                tokenS = Address.normalize(req.getPair.tokenS),
                tokenB = Address.normalize(req.getPair.tokenB)
              )
            )
          else
            req.market
        newReq.copy(
          owner = Address.normalize(req.owner),
          market = market
        )
      }
    case req: GetTrades.Req =>
      Future {
        if (req.owner.isEmpty)
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            "Parameter owner could not be empty"
          )
        val newReq = req.skip match {
          case Some(s) if s.size > maxItemsPerPage =>
            throw ErrorException(
              ErrorCode.ERR_INVALID_ARGUMENT,
              s"Parameter size of paging is larger than $maxItemsPerPage"
            )

          case Some(s) if s.skip < 0 =>
            throw ErrorException(
              ErrorCode.ERR_INVALID_ARGUMENT,
              s"Invalid parameter skip of paging:${s.skip}"
            )

          case Some(_) => req

          case None =>
            req.copy(skip = Some(Paging(size = defaultItemsPerPage)))
        }
        val market =
          if (req.market.isPair)
            GetTrades.Req.Market.Pair(
              req.getPair.copy(
                tokenS = Address.normalize(req.getPair.tokenS),
                tokenB = Address.normalize(req.getPair.tokenB)
              )
            )
          else
            req.market

        newReq.copy(
          owner = Address.normalize(req.owner),
          market = market
        )
      }
  }
}
