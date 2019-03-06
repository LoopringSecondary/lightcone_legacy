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
import io.lightcone.ethereum.TxStatus.TX_STATUS_PENDING
import io.lightcone.ethereum.event.AddressNonceChangedEvent

import scala.concurrent._

final class TxAddressNonceEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext)
    extends EventExtractor[TransactionData, AddressNonceChangedEvent] {

  def extractEvents(txdata: TransactionData) = Future {
    val txStatus = txdata.receiptAndHeaderOpt match {
      case None               => TX_STATUS_PENDING
      case Some((receipt, _)) => receipt.status
    }
    val nonce: BigInt = txdata.tx.nonce
    Seq(
      AddressNonceChangedEvent(
        txHash = txdata.tx.hash,
        from = txdata.tx.from,
        nonce = nonce.intValue(),
        txStatus = txStatus
      )
    )
  }
}
