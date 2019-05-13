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
import io.lightcone.ethereum.BlockHeader
import io.lightcone.ethereum.TxStatus.TX_STATUS_SUCCESS
import io.lightcone.ethereum.event.{BlockEvent, EventHeader}
import io.lightcone.ethereum.persistence._
import io.lightcone.lib._
import io.lightcone.relayer.data._

import scala.concurrent.{ExecutionContext, Future}

final class DefaultEventExtractor @Inject()(
    blockEventExtractor: EventExtractor[BlockWithTxObject, AnyRef]
  )(
    implicit
    val txEventExtractor: EventExtractor[TransactionData, AnyRef],
    val ec: ExecutionContext)
    extends EventExtractor[BlockWithTxObject, AnyRef] {

  def extractEvents(block: BlockWithTxObject) =
    for {
      blockEvents <- blockEventExtractor.extractEvents(block)
      transactions = (block.transactions zip block.receipts).map {
        case (tx, receipt) =>
          val eventHeader =
            EventHeader(
              txHash = tx.hash,
              txStatus = TX_STATUS_SUCCESS,
              blockHeader = Some(
                BlockHeader(
                  NumericConversion.toBigInt(block.number).longValue(),
                  block.hash,
                  block.miner,
                  NumericConversion.toBigInt(block.timestamp).longValue(),
                  block.uncles
                )
              )
            )
          TransactionData(tx, Some(receipt, eventHeader))
      }

      txEvents <- Future
        .sequence(
          transactions.map { txData =>
            txEventExtractor.extractEvents(txData).map { events =>
              if (!events.exists(_.isInstanceOf[Activity]))
                events ++ EventExtractor.extractDefaultActivity(txData)
              else events
            }
          }
        )
        .map(_.flatten)

      txActivities = txEvents
        .filter(_.isInstanceOf[Activity])
        .map(_.asInstanceOf[Activity])

      txActivityEvents: Seq[TxEvents] = EventExtractor.composeActivities(
        txActivities
      )

      fills = txEvents.filter(_.isInstanceOf[Fill]).map(_.asInstanceOf[Fill])

      txFillEvents: Seq[TxEvents] = EventExtractor.composeFills(fills)

      blockEvent = BlockEvent(
        blockNumber = NumericConversion.toBigInt(block.number).longValue(),
        txs = block.transactions.map(
          tx =>
            BlockEvent.Tx(
              from = tx.from,
              nonce = NumericConversion.toBigInt(tx.nonce).toInt,
              txHash = tx.hash
            )
        )
      )

      events = (blockEvent +: txEvents) ++ blockEvents ++ txActivityEvents ++ txFillEvents
    } yield events
}
