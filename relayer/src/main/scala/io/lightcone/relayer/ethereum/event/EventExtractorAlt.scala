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

package io.lightcone.relayer.ethereum.event

import io.lightcone.core._
import io.lightcone.ethereum.event._
import io.lightcone.lib._
import io.lightcone.relayer.data._

import scala.concurrent.{ExecutionContext, Future}

trait EventExtractorAlt {

  implicit val ec: ExecutionContext

  def extractTx(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: EventHeader
    ): Future[Seq[Any]]

  def extractBlock(block: RawBlockData): Future[Seq[Any]] =
    for {
      events <- Future.sequence {
        (block.txs zip block.receipts).map {
          case (tx, receipt) =>
            val eventHeader = getEventHeader(tx, receipt, BlockHeader()) //TODO: 确定blockHeader放置在哪里
            extractTx(tx, receipt, eventHeader)
          case _ => Future.successful(Seq.empty)
        }
      }
    } yield events.flatten

  def getEventHeader(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockHeader: BlockHeader
    ) =
    EventHeader(
      txHash = tx.hash,
      txFrom = Address.normalize(tx.from),
      txTo = Address.normalize(tx.to),
      txValue = NumericConversion.toBigInt(tx.value),
      txIndex = NumericConversion.toBigInt(tx.transactionIndex).intValue,
      txStatus = getStatus(receipt.status),
//      blockHash = tx.blockHash,
//      blockTimestamp = NumericConversion.toBigInt(blockTime).longValue,
//      blockNumber = NumericConversion.toBigInt(tx.blockNumber).longValue,
      blockHeader = Some(blockHeader),
      gasPrice = NumericConversion.toBigInt(tx.gasPrice).longValue,
      gasLimit = NumericConversion.toBigInt(tx.gas).intValue,
      gasUsed = NumericConversion.toBigInt(receipt.gasUsed).intValue
    )

  //TODO: 需要确定pending的status是什么
  def getStatus(status: String): TxStatus = {
    if (isSucceed(status)) TxStatus.TX_STATUS_SUCCESS
    else TxStatus.TX_STATUS_FAILED

  }

  def isSucceed(status: String): Boolean = {
    try {
      NumericConversion.toBigInt(status).intValue == 1
    } catch {
      case e: Throwable => false
    }
  }

}
