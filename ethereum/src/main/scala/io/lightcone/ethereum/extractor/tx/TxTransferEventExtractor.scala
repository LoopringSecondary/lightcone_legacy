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
import io.lightcone.ethereum.event.TransferEvent
import io.lightcone.lib.{Address, NumericConversion}

import scala.concurrent._

final class TxTransferEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext)
    extends EventExtractor[TransactionData, TransferEvent] {

  def extractEvents(txdata: TransactionData) = Future {
    val tx = txdata.tx
    val txValue = NumericConversion.toBigInt(tx.value)

    txdata.receiptAndHeaderOpt match {
      case Some((receipt, header)) if txValue > 0 =>
        receipt.logs.zipWithIndex.map {
          case (log, index) =>
            TransferEvent(
              header = Some(header),
              from = Address.normalize(tx.from),
              to = Address.normalize(tx.to),
              token = Address.ZERO.toString(),
              amount = txValue
            )
        }.flatMap { event =>
          Seq(event.copy(owner = event.from), event.copy(owner = event.to))
        }
      case _ => Nil
      // TODO(hongyu): we should also extract pening activities.
    }
  }
}
