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
import io.lightcone.ethereum.event._
import io.lightcone.lib._
import io.lightcone.relayer.data._

import scala.concurrent.{ExecutionContext, Future}

class BlockGasPriceExtractor @Inject()(
    implicit
    val ec: ExecutionContext)
    extends BlockEventExtractor[BlockGasPricesExtractedEvent] {

  def extractEvents(
      block: BlockWithTxObject
    ): Future[Seq[BlockGasPricesExtractedEvent]] = Future {
    Seq(
      BlockGasPricesExtractedEvent(
        height = NumericConversion.toBigInt(block.number).longValue,
        gasPrices = block.transactions.map { tx =>
          NumericConversion.toBigInt(tx.gasPrice).longValue
        }
      )
    )
  }
}
