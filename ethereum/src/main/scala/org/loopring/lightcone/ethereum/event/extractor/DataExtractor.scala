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

package org.loopring.lightcone.ethereum.event.extractor

import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import org.loopring.lightcone.ethereum.event._

trait DataExtractor[R] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[R]

  def getEventHeader(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): EventHeader = {
    EventHeader(
      txHash = tx.hash,
      txFrom = tx.from,
      txTo = tx.to,
      txValue = Numeric.toBigInt(tx.value).toByteArray,
      txIndex = Numeric.toBigInt(tx.transactionIndex).intValue(),
      txStatus = getStatus(receipt.status),
      blockHash = tx.blockHash,
      blockTimestamp = Numeric.toBigInt(blockTime).longValue(),
      blockNumber = Numeric.toBigInt(tx.blockNumber).longValue()
    )
  }

  def getStatus(status: String): TxStatus = {
    if (isSucceed(status)) {
      TxStatus.TX_STATUS_SUCCESS
    } else {
      TxStatus.TX_STATUS_FAILED
    }
  }

  def isSucceed(status: String): Boolean = {
    Numeric.toBigInt(status).intValue() == 1
  }
}
