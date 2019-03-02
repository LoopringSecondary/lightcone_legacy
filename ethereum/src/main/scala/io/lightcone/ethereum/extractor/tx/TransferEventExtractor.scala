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

package io.lightcone.ethereum.extractor

import com.google.inject.Inject
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.event
import io.lightcone.ethereum.event.{TransferEvent => PTransferEvent}
import io.lightcone.lib.{Address, NumericConversion}

import scala.collection.mutable.ListBuffer
import scala.concurrent._

class TransferEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext)
    extends TxEventExtractor[PTransferEvent] {

  def extractEvents(params: TransactionData): Future[Seq[PTransferEvent]] =
    Future {
      val (tx, receipt, eventHeader) =
        (params.tx, params.receipt, params.eventHeader)
      val txValue = NumericConversion.toBigInt(tx.value)
      val transfers = ListBuffer.empty[event.TransferEvent]
      receipt.logs.zipWithIndex.foreach {
        case (log, index) =>
          wethAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(transfer: TransferEvent.Result) =>
              transfers.append(
                event.TransferEvent(
                  Some(eventHeader),
                  from = transfer.from,
                  to = transfer.receiver,
                  token = log.address,
                  amount = transfer.amount
                )
              )
            case Some(withdraw: WithdrawalEvent.Result) =>
              transfers.append(
                event.TransferEvent(
                  Some(eventHeader),
                  from = withdraw.src,
                  to = log.address,
                  token = log.address,
                  amount = withdraw.wad
                ),
                event.TransferEvent(
                  Some(eventHeader),
                  from = log.address,
                  to = withdraw.src,
                  token = Address.ZERO.toString(),
                  amount = withdraw.wad
                )
              )
            case Some(deposit: DepositEvent.Result) =>
              transfers.append(
                event.TransferEvent(
                  Some(eventHeader),
                  from = log.address,
                  to = deposit.dst,
                  token = log.address,
                  amount = deposit.wad
                ),
                event.TransferEvent(
                  Some(eventHeader),
                  from = deposit.dst,
                  to = log.address,
                  token = Address.ZERO.toString(),
                  amount = deposit.wad
                )
              )
            case _ =>
          }
      }

      transfers.flatMap(
        event =>
          Seq(
            event.copy(
              from = Address.normalize(event.from),
              to = Address.normalize(event.to),
              token = Address.normalize(event.token),
              owner = Address.normalize(event.from),
              header = event.header
            ),
            event.copy(
              from = Address.normalize(event.from),
              to = Address.normalize(event.to),
              token = Address.normalize(event.token),
              owner = Address.normalize(event.to),
              header = event.header
            )
          )
      )
    }

}
