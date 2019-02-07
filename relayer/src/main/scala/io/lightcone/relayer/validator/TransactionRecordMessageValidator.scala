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
import io.lightcone.relayer.actors.TransactionRecordActor

import io.lightcone.core._
import io.lightcone.persistence._
import io.lightcone.relayer.data._
import scala.concurrent.{ExecutionContext, Future}

object TransactionRecordMessageValidator {
  val name = "transaction_record_validator"
}

final class TransactionRecordMessageValidator(
  )(
    implicit
    val config: Config,
    ec: ExecutionContext)
    extends MessageValidator {

  val transactionRecordConfig = config.getConfig(TransactionRecordActor.name)

  val defaultItemsPerPage =
    transactionRecordConfig.getInt("default-items-per-page")
  val maxItemsPerPage = transactionRecordConfig.getInt("max-items-per-page")

  def validate = {
    case req: TransferEvent =>
      Future {
        validate(req.header, req.owner)
        req
      }

    case req: CutoffEvent =>
      Future {
        validate(req.header, req.owner)
        req
      }

    case req: OrdersCancelledEvent =>
      Future {
        validate(req.header, req.owner)
        req
      }

    case req: OrderFilledEvent =>
      Future {
        validate(req.header, req.owner)
        if (req.orderHash.isEmpty)
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            "Parameter orderHash is empty"
          )
        req
      }
    case req: GetTransactionRecords.Req =>
      Future {
        val owner =
          if (req.owner.isEmpty)
            throw ErrorException(
              ErrorCode.ERR_INVALID_ARGUMENT,
              "Parameter owner could not be empty"
            )
          else Address.normalize(req.owner)
        GetTransactionRecords.Req(
          owner,
          req.queryType,
          req.sort,
          getValidSkip(req.paging)
        )
      }

    case req: GetTransactionRecordCount.Req =>
      Future {
        val owner =
          if (req.owner.isEmpty)
            throw ErrorException(
              ErrorCode.ERR_INVALID_ARGUMENT,
              "Parameter owner could not be empty"
            )
          else Address.normalize(req.owner)
        req.copy(owner = owner)
      }
  }

  private def getValidSkip(paging: Option[CursorPaging]) = {
    paging match {
      case Some(s) if s.size > maxItemsPerPage =>
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          s"Parameter size of paging is larger than $maxItemsPerPage"
        )

      case Some(s) if s.cursor < 0 =>
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          s"Invalid parameter cursor of paging:${s.cursor}"
        )

      case Some(s) => paging

      case None =>
        Some(CursorPaging(size = defaultItemsPerPage))
    }
  }

  private def validate(
      headerOpt: Option[EventHeader],
      owner: String
    ) {
    if (headerOpt.isEmpty || owner.isEmpty)
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        "Parameter header and owner could not be empty"
      )

    val header = headerOpt.get
    if (header.txHash.isEmpty ||
        header.blockHash.isEmpty ||
        header.blockTimestamp < 0 ||
        header.txFrom.isEmpty ||
        header.txTo.isEmpty ||
        header.gasPrice < 0 ||
        header.gasLimit < 0 ||
        header.gasUsed < 0)
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        s"Invalid value in header:$header"
      )

    if (header.blockNumber < 0 ||
        header.txIndex < 0 ||
        header.logIndex < 0 ||
        header.eventIndex < 0)
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        s"Invalid index in header:$header"
      )

    if (header.blockNumber > 500000000)
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        s"Parameter blockNumber larger than 500000000 in ${header}"
      )

    if (header.txIndex > 4096 || header.logIndex > 4096)
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        s"Parameters txIndex or logIndex larger than 4096 in ${header}"
      )

    if (header.eventIndex > 1024)
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        s"Parameter eventIndex larger than 1024 in ${header}"
      )
  }
}
