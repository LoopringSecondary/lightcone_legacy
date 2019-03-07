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

      txEvents <- Future.sequence(
        transactions.map(txEventExtractor.extractEvents)
      )
      //TODO(HONGYU): 可能需要理一下前端的展示流程，当前的是否满足，
      //是否当没有任何Activity时，是否需要生成一个
      events = blockEvents ++ txEvents.flatten
    } yield events
}
