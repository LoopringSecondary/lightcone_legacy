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
import io.lightcone.ethereum.event.EventHeader
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
    for {
      events <- Future.sequence {
        blockExtractors.map(_.extractEvents(block))
      }
    } yield events.flatten

  def extractEvents(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: EventHeader
    ): Future[Seq[AnyRef]] = {
    val txData = TransactionData(tx, receipt, eventHeader)
    Future.sequence {
      txExtractors.map(_.extractEvents(txData))
    }
  }

}
