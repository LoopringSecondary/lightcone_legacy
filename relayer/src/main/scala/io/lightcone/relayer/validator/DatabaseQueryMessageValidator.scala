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
import io.lightcone.relayer.data.GetRings.Req.Filter._
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
  implicit val pageConfig = PageConfig(defaultItemsPerPage, maxItemsPerPage)

  def validate = {
    case req: GetOrders.Req =>
      Future {
        val owner =
          if (req.owner.isEmpty)
            throw ErrorException(
              ERR_INVALID_ARGUMENT,
              "Parameter owner could not be empty"
            )
          else MessageValidator.normalizeAddress(req.owner)
        val marketOpt = req.marketPair match {
          case Some(m) =>
            val base = MessageValidator.normalizeAddress(m.baseToken)
            val quote = MessageValidator.normalizeAddress(m.quoteToken)
            Some(MarketPair(base, quote))
          case _ => None
        }
        req.copy(
          owner = owner,
          marketPair = marketOpt,
          skip = MessageValidator.getValidPaging(req.skip)
        )
      }

    case req: GetUserFills.Req =>
      Future {
        if (req.owner.isEmpty)
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            "Parameter owner could not be empty"
          )
        val ringOpt = req.ring match {
          case Some(r) =>
            val ringHash =
              MessageValidator.normalizeHash(r.ringHash)
            val ringIndex =
              if (r.ringIndex.nonEmpty && !MessageValidator.isValidNumber(
                    r.ringIndex
                  ))
                throw ErrorException(
                  ERR_INVALID_ARGUMENT,
                  s"invalid ringIndex:${r.ringIndex}"
                )
              else r.ringIndex
            val fillIndex =
              if (r.fillIndex.nonEmpty && !MessageValidator.isValidNumber(
                    r.fillIndex
                  ))
                throw ErrorException(
                  ERR_INVALID_ARGUMENT,
                  s"invalid fillIndex:${r.fillIndex}"
                )
              else r.fillIndex
            Some(GetUserFills.Req.RingFilter(ringHash, ringIndex, fillIndex))
          case _ => None
        }
        val marketOpt = req.marketPair match {
          case Some(m) =>
            val base = MessageValidator.normalizeAddress(m.baseToken)
            val quote = MessageValidator.normalizeAddress(m.quoteToken)
            Some(MarketPair(base, quote))
          case _ => None
        }
        GetUserFills.Req(
          MessageValidator.normalizeAddress(req.owner),
          MessageValidator.normalizeHash(req.txHash),
          MessageValidator.normalizeHash(req.orderHash),
          marketOpt,
          ringOpt,
          MessageValidator.normalizeAddress(req.wallet),
          MessageValidator.normalizeAddress(req.miner),
          req.sort,
          MessageValidator.getValidPaging(req.paging)
        )
      }

    case req: GetMarketFills.Req =>
      Future {
        val marketOpt = req.marketPair match {
          case Some(m) =>
            if (m.baseToken.isEmpty || m.quoteToken.isEmpty) {
              throw ErrorException(
                ERR_INVALID_ARGUMENT,
                s"invalid marketPair:${req.marketPair}"
              )
            }
            val base = MessageValidator.normalizeAddress(m.baseToken)
            val quote = MessageValidator.normalizeAddress(m.quoteToken)
            Some(MarketPair(base, quote))
          case _ =>
            throw ErrorException(
              ERR_INVALID_ARGUMENT,
              s"marketPair is required"
            )
        }
        req.copy(marketPair = marketOpt)
      }

    case req: GetRings.Req =>
      Future {
        val filter = req.filter match {
          case RingHash(r) => RingHash(MessageValidator.normalizeHash(r))
          case RingIndex(i) if i < 0 =>
            throw ErrorException(
              ERR_INVALID_ARGUMENT,
              s"invalid ringIndex:${i}"
            )
          case RingIndex(i) => req.filter
          case Empty        => req.filter
        }
        req.copy(
          paging = MessageValidator.getValidPaging(req.paging),
          filter = filter
        )
      }
  }

}
