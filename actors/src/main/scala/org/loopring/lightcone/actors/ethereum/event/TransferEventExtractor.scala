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

package org.loopring.lightcone.actors.ethereum.event

import com.google.inject.Inject
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto.{TransferEvent => PTransferEvent, _}
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.base.MetadataManager
import org.web3j.utils.Numeric

import scala.collection.mutable.ListBuffer
import scala.concurrent._

class TransferEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    val metadataManager: MetadataManager)
    extends EventExtractor[PTransferEvent] {

  val wethAddress = Address(
    metadataManager.getTokenBySymbol("weth").get.meta.address
  )

  def extract(block: RawBlockData): Future[Seq[PTransferEvent]] = Future {
    val transfers = ListBuffer.empty[PTransferEvent]
    (block.txs zip block.receipts).foreach {
      case (tx, receipt) =>
        val header = getEventHeader(tx, receipt, block.timestamp)
        val txValue = BigInt(Numeric.toBigInt(formatHex(tx.value)))
        if (isSucceed(receipt.status)) {
          receipt.logs.zipWithIndex.foreach {
            case (log, index) =>
              wethAbi.unpackEvent(log.data, log.topics.toArray) match {
                case Some(transfer: TransferEvent.Result) =>
                  transfers.append(
                    PTransferEvent(
                      Some(header.withLogIndex(index)),
                      from = transfer.from,
                      to = transfer.receiver,
                      token = log.address,
                      amount = transfer.amount
                    )
                  )
                case Some(withdraw: WithdrawalEvent.Result) =>
                  transfers.append(
                    PTransferEvent(
                      Some(header.withLogIndex(index)),
                      from = withdraw.src,
                      to = log.address,
                      token = log.address,
                      amount = withdraw.wad
                    ),
                    PTransferEvent(
                      Some(header.withLogIndex(index)),
                      from = log.address,
                      to = withdraw.src,
                      token = Address.ZERO.toString(),
                      amount = withdraw.wad
                    )
                  )
                case Some(deposit: DepositEvent.Result) =>
                  transfers.append(
                    PTransferEvent(
                      Some(header.withLogIndex(index)),
                      from = log.address,
                      to = deposit.dst,
                      token = log.address,
                      amount = deposit.wad
                    ),
                    PTransferEvent(
                      Some(header.withLogIndex(index)),
                      from = deposit.dst,
                      to = log.address,
                      token = Address.ZERO.toString(),
                      amount = deposit.wad
                    )
                  )
                case _ =>
              }
          }
          if (txValue > 0 && !Address(
                tx.to
              ).equals(wethAddress)) {
            transfers.append(
              PTransferEvent(
                header = Some(header),
                from = tx.from,
                to = tx.to,
                token = Address.ZERO.toString(),
                amount = txValue
              )
            )
          }
        } else {
          wethAbi.unpackFunctionInput(tx.input) match {
            case Some(transfer: TransferFunction.Parms) =>
              transfers.append(
                PTransferEvent(
                  header = Some(header),
                  from = tx.from,
                  to = transfer.to,
                  token = tx.to,
                  amount = transfer.amount
                )
              )
            case Some(transferFrom: TransferFromFunction.Parms) =>
              transfers.append(
                PTransferEvent(
                  header = Some(header),
                  from = transferFrom.txFrom,
                  to = transferFrom.to,
                  token = tx.to,
                  amount = transferFrom.amount
                )
              )
            case Some(_: DepositFunction.Parms) =>
              transfers.append(
                PTransferEvent(
                  header = Some(header),
                  from = tx.to,
                  to = tx.from,
                  token = tx.to,
                  amount = txValue
                ),
                PTransferEvent(
                  header = Some(header),
                  from = tx.from,
                  to = tx.to,
                  token = Address.ZERO.toString(),
                  amount = txValue
                )
              )
            case Some(withdraw: WithdrawFunction.Parms) =>
              transfers.append(
                PTransferEvent(
                  header = Some(header),
                  from = tx.from,
                  to = tx.to,
                  token = tx.to,
                  amount = withdraw.wad
                ),
                PTransferEvent(
                  header = Some(header),
                  from = tx.to,
                  to = tx.from,
                  token = Address.ZERO.toString(),
                  amount = withdraw.wad
                )
              )
            case _ =>
              if (txValue > 0) {
                transfers.append(
                  PTransferEvent(
                    header = Some(header),
                    from = tx.from,
                    to = tx.to,
                    token = Address.ZERO.toString(),
                    amount = txValue
                  )
                )
                if (Address(tx.to).equals(wethAddress)) {
                  transfers.append(
                    PTransferEvent(
                      header = Some(header),
                      from = tx.to,
                      to = tx.from,
                      token = tx.to,
                      amount = txValue
                    )
                  )
                }
              }
          }
        }
    }
    transfers.flatMap(
      event => Seq(event.withOwner(event.from), event.withOwner(event.to))
    )
  }
}
