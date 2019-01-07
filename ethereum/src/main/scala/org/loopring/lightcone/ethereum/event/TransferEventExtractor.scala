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

package org.loopring.lightcone.ethereum.event

import org.loopring.lightcone.ethereum.abi.{
  DepositEvent,
  TransferEvent,
  WithdrawalEvent
}
import org.loopring.lightcone.proto.{TransferEvent => PTransferEvent, _}

class TransferEventExtractor extends DataExtractor[PTransferEvent] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[PTransferEvent] = {
    receipt.logs
      .map(log => {
        wethAbi.unpackEvent(log.data, log.topics.toArray) match {
          case Some(transfer: TransferEvent.Result) =>
            Some(
              PTransferEvent(
                header = Some(getEventHeader(tx, receipt, blockTime)),
                from = transfer.from,
                to = transfer.receiver,
                amount = transfer.amount.toByteArray
              )
            )
          case Some(withdraw: WithdrawalEvent.Result) =>
            Some(
              PTransferEvent(
                header = Some(getEventHeader(tx, receipt, blockTime)),
                from = withdraw.src,
                to = log.address,
                amount = withdraw.wad.toByteArray
              )
            )
          case Some(deposit: DepositEvent.Result) =>
            Some(
              PTransferEvent(
                header = Some(getEventHeader(tx, receipt, blockTime)),
                from = log.address,
                to = deposit.dst,
                amount = deposit.wad.toByteArray
              )
            )
          case _ => None
        }
      })
      .filter(_.nonEmpty)
      .map(_.get)

  }
}
