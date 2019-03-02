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

import io.lightcone.ethereum.TxStatus.TX_STATUS_PENDING
import io.lightcone.ethereum.event.EventHeader
import io.lightcone.relayer.data._

import scala.concurrent.Future

case class TransactionData(
    tx: Transaction,
    receipt: TransactionReceipt,
    eventHeader: EventHeader)

trait TxEventExtractor[R] extends EventExtractor[TransactionData, R] {

  final def extractEvents(tx: TransactionData): Future[Seq[R]] = {
    if (tx.eventHeader.txStatus == TX_STATUS_PENDING) {
      extractPendingEvents(tx.tx)
    } else {
      extractBlockedEvents(tx)
    }
  }

  def extractPendingEvents(tx: Transaction): Future[Seq[R]]

  def extractBlockedEvents(tx: TransactionData): Future[Seq[R]]
}
