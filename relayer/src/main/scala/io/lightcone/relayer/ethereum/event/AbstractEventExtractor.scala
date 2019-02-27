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

import io.lightcone.ethereum._
import io.lightcone.ethereum.event._
import io.lightcone.lib._
import io.lightcone.relayer.data._

import scala.concurrent.{ExecutionContext, Future}

abstract class AbstractEventExtractor extends EventExtractor {
  implicit val ec: ExecutionContext

  def extractEventsFromTx(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: EventHeader
    ): Future[Seq[AnyRef]]

  def extractEvents(block: RawBlockData): Future[Seq[AnyRef]] =
    for {
      events <- Future.sequence {
        (block.txs zip block.receipts).map {
          case (tx, receipt) =>
            val eventHeader = getEventHeader(
              tx,
              receipt,
              BlockHeader(
                height = block.height,
                hash = block.hash,
                miner = block.miner,
                timestamp =
                  NumericConversion.toBigInt(block.timestamp).longValue(),
                uncles = block.uncles
              )
            ) //TODO: 确定blockHeader放置在哪里
            extractEventsFromTx(tx, receipt, eventHeader)
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
      blockHeader = Some(blockHeader)
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
