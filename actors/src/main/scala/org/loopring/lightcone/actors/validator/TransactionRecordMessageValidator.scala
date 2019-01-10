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
import org.loopring.lightcone.actors.core.TransactionRecordActor
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto._

object TransactionRecordMessageValidator {
  val name = "transaction_record_validator"
}

final class TransactionRecordMessageValidator()(implicit val config: Config)
    extends MessageValidator {

  val transactionRecordConfig = config.getConfig(TransactionRecordActor.name)

  val defaultItemsPerPage =
    transactionRecordConfig.getInt("default-items-per-page")
  val maxItemsPerPage = transactionRecordConfig.getInt("max-items-per-page")

  def validate = {
    case req: TransferEvent =>
      validate(req.header, req.owner)
      req

    case req: CutoffEvent =>
      validate(req.header, req.owner)
      req

    case req: OrdersCancelledEvent =>
      validate(req.header, req.owner)
      req

    case req: OrderFilledEvent =>
      validate(req.header, req.owner)
      if (req.orderHash.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Parameter orderHash is empty"
        )
      req

    case req: GetTransactionRecords.Req =>
      if (req.owner.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Parameter owner could not be empty"
        )

      req.paging match {
        case Some(p) if p.size > maxItemsPerPage =>
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            s"Parameter size of paging is larger than $maxItemsPerPage"
          )

        case Some(p) if p.cursor < 0 =>
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            s"Invalid parameter cursor of paging:${p.cursor}"
          )

        case Some(_) => req

        case None =>
          req.copy(paging = Some(CursorPaging(size = defaultItemsPerPage)))
      }

    case req: GetTransactionRecordCount.Req =>
      if (req.owner.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Parameter owner could not be empty"
        )
      req
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
    if (header.blockNumber < 0 ||
        header.txIndex < 0 ||
        header.logIndex < 0 ||
        header.eventIndex < 0)
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        s"Invalid index in header:$header"
      )

    if (header.blockNumber > 99999999)
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        s"Parameter blockNumber larger than 99999999 in ${header}"
      )

    if (header.txIndex > 9999 || header.logIndex > 9999)
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        s"Parameters txIndex or logIndex larger than 9999 in ${header}"
      )

    if (header.eventIndex > 999)
      throw ErrorException(
        ErrorCode.ERR_INVALID_ARGUMENT,
        s"Parameter eventIndex larger than 999 in ${header}"
      )
  }
}
