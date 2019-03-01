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
import com.google.protobuf.ByteString
import io.lightcone.core.Amount
import io.lightcone.ethereum.event.{EventHeader, TransferEvent}
import io.lightcone.ethereum.{BlockHeader, TxStatus}
import io.lightcone.lib.{Address, NumericConversion}
import io.lightcone.relayer.data.{RawBlockData, Transaction, TransactionReceipt}

import scala.concurrent.{ExecutionContext, Future}

object EventExtractorCompose {

  def default()(implicit ec: ExecutionContext): EventExtractorCompose = {
    new EventExtractorCompose()
  }
}

class EventExtractorCompose @Inject()(
    implicit
    val ec: ExecutionContext) {

  var txExtractors = Seq.empty[TxEventExtractor[_]]

  var blockExtractors = Seq.empty[BlockEventExtractor[_]]

  def registerTxExtractor(extractors: TxEventExtractor[_]*) = {
    txExtractors = txExtractors ++ extractors
    this
  }

  def registerBlockExtractor(extractors: BlockEventExtractor[_]*) = {
    blockExtractors = blockExtractors ++ extractors
    this
  }

  def extractEvents(block: RawBlockData): Future[Seq[Any]] =
    Future.sequence {
      blockExtractors.map(_.extractEvents(block))
    }.map(_.flatten)

  def extractEventsByTx(block: RawBlockData): Future[Seq[Any]] = {
    val header = BlockHeader(
      hash = block.hash,
      height = block.height,
      miner = block.miner,
      timestamp = NumericConversion.toBigInt(block.timestamp).longValue(),
      uncles = block.uncles
    )
    for {
      txEvents <- Future
        .sequence((block.txs zip block.receipts).map {
          case (tx, receipt) =>
            val eventHeader = EventHeader(
              txHash = tx.hash,
              txStatus =
                if (NumericConversion.toBigInt(receipt.status) == 1)
                  TxStatus.TX_STATUS_SUCCESS
                else TxStatus.TX_STATUS_FAILED,
              blockHeader = Some(header),
              txFrom = tx.from,
              txTo = tx.to,
              txValue = Some(NumericConversion.toAmount(tx.value))
            )
            for {
              events <- extractEvents(tx, receipt, eventHeader)
            } yield {
              if (events.isEmpty)
                Seq(
                  TransferEvent(
                    header = Some(eventHeader),
                    owner = tx.from,
                    from = tx.from,
                    to = tx.to,
                    token = Address.ZERO.toString(),
                    amount = Some(
                      Amount(value = ByteString.copyFrom(BigInt(0).toByteArray))
                    )
                  )
                )
              else events
            }
          case _ => Future.successful(Seq.empty)
        })
        .map(_.flatten)
      blockEvents <- extractEvents(block)
    } yield txEvents ++ blockEvents
  }

  def extractEvents(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: EventHeader
    ): Future[Seq[Any]] = {
    val txData = TransactionData(tx, receipt, eventHeader)
    Future.sequence {
      txExtractors.map(_.extractEvents(txData))
    }
  }

}
