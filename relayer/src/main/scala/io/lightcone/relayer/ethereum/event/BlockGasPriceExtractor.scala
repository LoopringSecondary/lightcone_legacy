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

import akka.util.Timeout
import com.google.inject.Inject
import io.lightcone.lib._
import io.lightcone.ethereum.event._
import io.lightcone.relayer.data._

import scala.concurrent.{ExecutionContext, Future}

class BlockGasPriceExtractor @Inject()(
    implicit
    val timeout: Timeout,
    val ec: ExecutionContext)
    extends AbstractEventExtractor {

  def extractTx(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: EventHeader
    ): Future[Seq[AnyRef]] = Future {
    Seq.empty
  }

  override def extractBlock(
      block: RawBlockData
    ): Future[Seq[BlockGasPricesExtractedEvent]] =
    Future {
      Seq(
        BlockGasPricesExtractedEvent(
          height = block.height,
          gasPrices = block.txs.map { tx =>
            NumericConversion.toBigInt(tx.gasPrice).longValue
          }
        )
      )
    }
}
