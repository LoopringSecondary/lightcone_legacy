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
import com.typesafe.config.Config
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto.{TransferEvent => PTransferEvent, _}
import org.loopring.lightcone.actors.data._
import org.web3j.utils.Numeric
import scala.collection.mutable.ListBuffer
import scala.concurrent._

class TransferEventExtractor @Inject()(
    implicit
    val config: Config,
    val ec: ExecutionContext)
    extends EventExtractor[PTransferEvent] {
  // TODO (yadong) 等待永丰的PR完成，改成从统一的数据库获取。
  val wethAddress = Address(config.getString("weth.address"))

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Future[Seq[PTransferEvent]] = Future {
    val transfers = ListBuffer.empty[PTransferEvent]
    val header = getEventHeader(tx, receipt, blockTime)
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
                  amount = transfer.amount.toByteArray
                )
              )
            case Some(withdraw: WithdrawalEvent.Result) =>
              transfers.append(
                PTransferEvent(
                  Some(header.withLogIndex(index)),
                  from = withdraw.src,
                  to = log.address,
                  token = log.address,
                  amount = withdraw.wad.toByteArray
                ),
                PTransferEvent(
                  Some(header.withLogIndex(index)),
                  from = log.address,
                  to = withdraw.src,
                  token = Address.ZERO.toString(),
                  amount = withdraw.wad.toByteArray
                )
              )
            case Some(deposit: DepositEvent.Result) =>
              transfers.append(
                PTransferEvent(
                  Some(header.withLogIndex(index)),
                  from = log.address,
                  to = deposit.dst,
                  token = log.address,
                  amount = deposit.wad.toByteArray
                ),
                PTransferEvent(
                  Some(header.withLogIndex(index)),
                  from = deposit.dst,
                  to = log.address,
                  token = Address.ZERO.toString(),
                  amount = deposit.wad.toByteArray
                )
              )
            case _ =>
          }
      }
      if (BigInt(Numeric.toBigInt(tx.value)) > 0 && !Address(tx.to)
            .equals(wethAddress)) {
        transfers.append(
          PTransferEvent(
            header = Some(header),
            from = tx.from,
            to = tx.to,
            token = Address.ZERO.toString(),
            amount = Numeric.toBigInt(tx.value).toByteArray
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
              amount = transfer.amount.toByteArray
            )
          )
        case Some(transferFrom: TransferFromFunction.Parms) =>
          transfers.append(
            PTransferEvent(
              header = Some(header),
              from = transferFrom.txFrom,
              to = transferFrom.to,
              token = tx.to,
              amount = transferFrom.amount.toByteArray
            )
          )
        case Some(_: DepositFunction.Parms) =>
          transfers.append(
            PTransferEvent(
              header = Some(header),
              from = tx.to,
              to = tx.from,
              token = tx.to,
              amount = Numeric.toBigInt(tx.value).toByteArray
            ),
            PTransferEvent(
              header = Some(header),
              from = tx.from,
              to = tx.to,
              token = Address.ZERO.toString(),
              amount = Numeric.toBigInt(tx.value).toByteArray
            )
          )
        case Some(withdraw: WithdrawFunction.Parms) =>
          transfers.append(
            PTransferEvent(
              header = Some(header),
              from = tx.from,
              to = tx.to,
              token = tx.to,
              amount = withdraw.wad.toByteArray
            ),
            PTransferEvent(
              header = Some(header),
              from = tx.to,
              to = tx.from,
              token = Address.ZERO.toString(),
              amount = withdraw.wad.toByteArray
            )
          )
        case _ =>
          if (BigInt(Numeric.toBigInt(tx.value)) > 0) {
            transfers.append(
              PTransferEvent(
                header = Some(header),
                from = tx.from,
                to = tx.to,
                token = Address.ZERO.toString(),
                amount = Numeric.toBigInt(tx.value).toByteArray
              )
            )
            if (Address(tx.to).equals(wethAddress)) {
              transfers.append(
                PTransferEvent(
                  header = Some(header),
                  from = tx.to,
                  to = tx.from,
                  token = tx.to,
                  amount = Numeric.toBigInt(tx.value).toByteArray
                )
              )
            }
          }
      }
    }
    transfers.flatMap(
      event => Seq(event.withOwner(event.from), event.withOwner(event.to))
    )
  }
}
