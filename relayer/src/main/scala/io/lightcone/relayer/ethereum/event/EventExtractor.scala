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

import io.lightcone.relayer.data._
import io.lightcone.core._
import org.web3j.utils.Numeric

import scala.concurrent.{ExecutionContext, Future}

trait EventExtractor[R] {

  implicit val ec: ExecutionContext

  def extract(block: RawBlockData): Future[Seq[R]]

  def getEventHeader(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ) =
    EventHeader(
      txHash = tx.hash,
      txFrom = Address.normalize(tx.from),
      txTo = Address.normalize(tx.to),
      txValue = BigInt(Numeric.toBigInt(formatHex(tx.value))),
      txIndex = Numeric.toBigInt(formatHex(tx.transactionIndex)).intValue(),
      txStatus = getStatus(receipt.status),
      blockHash = tx.blockHash,
      blockTimestamp = Numeric.toBigInt(formatHex(blockTime)).longValue(),
      blockNumber = Numeric.toBigInt(formatHex(tx.blockNumber)).longValue(),
      gasPrice = Numeric.toBigInt(formatHex(tx.gasPrice)).longValue(),
      gasLimit = Numeric.toBigInt(formatHex(tx.gas)).intValue(),
      gasUsed = Numeric.toBigInt(formatHex(receipt.gasUsed)).intValue()
    )

  def getStatus(status: String): TxStatus = {
    if (isSucceed(status)) TxStatus.TX_STATUS_SUCCESS
    else TxStatus.TX_STATUS_FAILED

  }

  def isSucceed(status: String): Boolean = {
    try {
      Numeric.toBigInt(formatHex(status)).intValue() == 1
    } catch {
      case e: Throwable => false
    }
  }

  def hex2ArrayBytes(str: String): Array[Byte] = {
    Numeric.toBigInt(formatHex(str)).toByteArray
  }
}
