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

// Owner: Yongfeng
object DatabaseQueryMessageValidator {
  val name = "database_query_validator"
}

final class DatabaseQueryMessageValidator()(implicit val config: Config)
    extends MessageValidator {

  val defaultItemsPerPage =
    config.getInt("default-items-per-page")
  val maxItemsPerPage = config.getInt("max-items-per-page")

  val numberRegex = """^\d+$""".r

  def validate = {
    case req: GetOrdersForUser.Req =>
      val owner =
        if (req.owner.isEmpty)
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            "Parameter owner could not be empty"
          )
        else Address.normalize(req.owner)
      val marketOpt = req.market match {
        case Some(m) =>
          val tokenS =
            if (m.tokenS.nonEmpty) Address.normalize(m.tokenS) else ""
          val tokenB =
            if (m.tokenB.nonEmpty) Address.normalize(m.tokenB) else ""
          Some(GetOrdersForUser.Req.Market(tokenS, tokenB, m.isQueryBothSide))
        case _ => None
      }
      req.copy(owner = owner, market = marketOpt, skip = getValidSkip(req.skip))

    case req: GetTrades.Req =>
      val owner =
        if (req.owner.nonEmpty) Address.normalize(req.owner) else ""
      val txHash = if (req.txHash.nonEmpty) req.txHash.toLowerCase else ""
      val orderHash =
        if (req.orderHash.nonEmpty) req.orderHash.toLowerCase else ""
      val ringOpt = req.ring match {
        case Some(r) =>
          val ringHash = if (r.ringHash.nonEmpty) r.ringHash.toLowerCase else ""
          val ringIndex =
            if (r.ringIndex.nonEmpty && !isValidNumber(r.ringIndex))
              throw ErrorException(
                ErrorCode.ERR_INVALID_ARGUMENT,
                s"invalid ringIndex:${r.ringIndex}"
              )
            else r.ringIndex
          val fillIndex =
            if (r.fillIndex.nonEmpty && !isValidNumber(r.fillIndex))
              throw ErrorException(
                ErrorCode.ERR_INVALID_ARGUMENT,
                s"invalid fillIndex:${r.fillIndex}"
              )
            else r.fillIndex
          Some(GetTrades.Req.Ring(ringHash, ringIndex, fillIndex))
        case _ => None
      }
      val marketOpt = req.market match {
        case Some(m) =>
          val tokenS =
            if (m.tokenS.nonEmpty) Address.normalize(m.tokenS) else ""
          val tokenB =
            if (m.tokenB.nonEmpty) Address.normalize(m.tokenB) else ""
          Some(GetTrades.Req.Market(tokenS, tokenB, m.isQueryBothSide))
        case _ => None
      }
      val wallet =
        if (req.wallet.nonEmpty) Address.normalize(req.wallet) else ""
      val miner =
        if (req.miner.nonEmpty) Address.normalize(req.miner) else ""
      GetTrades.Req(
        owner,
        txHash,
        orderHash,
        ringOpt,
        marketOpt,
        wallet,
        miner,
        req.sort,
        getValidSkip(req.skip)
      )

    case req: GetRings.Req =>
      req.copy(skip = getValidSkip(req.skip))
  }

  private def isValidNumber(str: String) = {
    numberRegex.findAllIn(str).nonEmpty
  }

  private def getValidSkip(paging: Option[Paging]) = {
    paging match {
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

      case Some(s) => paging

      case None =>
        Some(Paging(size = defaultItemsPerPage))
    }
  }
}
