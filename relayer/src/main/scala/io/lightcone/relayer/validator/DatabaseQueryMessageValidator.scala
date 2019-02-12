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
import io.lightcone.persistence._
import io.lightcone.relayer.data._
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

  import ErrorCode._

  val defaultItemsPerPage =
    config.getInt("default-items-per-page")
  val maxItemsPerPage = config.getInt("max-items-per-page")

  def validate = {
    case req: GetOrdersForUser.Req =>
      Future {
        val owner =
          if (req.owner.isEmpty)
            throw ErrorException(
              ERR_INVALID_ARGUMENT,
              "Parameter owner could not be empty"
            )
          else normalizeAddress(req.owner)
        val marketOpt = req.market match {
          case Some(m) =>
            val tokenS = normalizeAddress(m.tokenS)
            val tokenB = normalizeAddress(m.tokenB)
            Some(GetOrdersForUser.Req.Market(tokenS, tokenB, m.isQueryBothSide))
          case _ => None
        }
        req.copy(
          owner = owner,
          market = marketOpt,
          skip = getValidSkip(req.skip)
        )
      }

    case req: GetTrades.Req =>
      Future {
        val owner = normalizeAddress(req.owner)
        val ringOpt = req.ring match {
          case Some(r) =>
            val ringHash =
              normalizeHash(r.ringHash)
            val ringIndex =
              if (r.ringIndex.nonEmpty && !isValidNumber(r.ringIndex))
                throw ErrorException(
                  ERR_INVALID_ARGUMENT,
                  s"invalid ringIndex:${r.ringIndex}"
                )
              else r.ringIndex
            val fillIndex =
              if (r.fillIndex.nonEmpty && !isValidNumber(r.fillIndex))
                throw ErrorException(
                  ERR_INVALID_ARGUMENT,
                  s"invalid fillIndex:${r.fillIndex}"
                )
              else r.fillIndex
            Some(GetTrades.Req.Ring(ringHash, ringIndex, fillIndex))
          case _ => None
        }
        val marketOpt = req.market match {
          case Some(m) =>
            val tokenS = normalizeAddress(m.tokenS)
            val tokenB = normalizeAddress(m.tokenB)
            Some(GetTrades.Req.Market(tokenS, tokenB, m.isQueryBothSide))
          case _ => None
        }
        GetTrades.Req(
          owner,
          normalizeHash(req.txHash),
          normalizeHash(req.orderHash),
          ringOpt,
          marketOpt,
          normalizeAddress(req.wallet),
          normalizeAddress(req.miner),
          req.sort,
          getValidSkip(req.skip)
        )
      }

    case req: GetRings.Req =>
      Future {
        req.copy(skip = getValidSkip(req.skip))
      }
  }

  private def normalizeAddress(address: String) = {
    if (address.nonEmpty) Address.normalize(address) else ""
  }

  private def normalizeHash(hash: String) = {
    if (hash.nonEmpty) hash.toLowerCase else ""
  }

  private def isValidNumber(str: String) = {
    try {
      str.toLong
      true
    } catch {
      case _: Throwable => false
    }
  }

  private def getValidSkip(paging: Option[Paging]) = {
    paging match {
      case Some(s) if s.size > maxItemsPerPage =>
        throw ErrorException(
          ERR_INVALID_ARGUMENT,
          s"Parameter size of paging is larger than $maxItemsPerPage"
        )

      case Some(s) if s.skip < 0 =>
        throw ErrorException(
          ERR_INVALID_ARGUMENT,
          s"Invalid parameter skip of paging:${s.skip}"
        )

      case Some(s) => paging

      case None =>
        Some(Paging(size = defaultItemsPerPage))
    }
  }
}
