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
import io.lightcone.ethereum.event.EventHeader
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.data._

import scala.concurrent.{ExecutionContext, Future}

final class DefaultEventExtractor @Inject()(
  )(
    implicit
    val ec: ExecutionContext,
    val weth: String,
    val protocol: String)
    extends EventExtractor[BlockWithTxObject, AnyRef] {

  // TODO(hongyu): add more block/tx-event extractors here in the list.
  private val blockEventExtractor: EventExtractor[BlockWithTxObject, AnyRef] =
    EventExtractor.compose[BlockWithTxObject, AnyRef]( //
      new BlockGasPriceExtractor() // more block event extractors
    )

  private val txEventExtractor: EventExtractor[TransactionData, AnyRef] =
    EventExtractor.compose[TransactionData, AnyRef]( //
      new TxTransferEventExtractor()(ec, weth, protocol) // more tx event extractors
    )

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

      txEvents <- Future.sequence(
        transactions.map(txEventExtractor.extractEvents)
      )
      events = blockEvents ++ txEvents.flatten
    } yield events
}
