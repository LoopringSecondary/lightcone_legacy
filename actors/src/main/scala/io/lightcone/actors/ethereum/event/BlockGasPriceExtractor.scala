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

package io.lightcone.actors.ethereum.event

import akka.util.Timeout
import com.google.inject.Inject
import io.lightcone.proto._
import io.lightcone.core._
import io.lightcone.ethereum.data._
import org.web3j.utils.Numeric
import scala.concurrent.{ExecutionContext, Future}

class BlockGasPriceExtractor @Inject()(
    implicit
    val timeout: Timeout,
    val ec: ExecutionContext)
    extends EventExtractor[BlockGasPrices] {

  override def extract(block: RawBlockData): Future[Seq[BlockGasPrices]] =
    Future {
      Seq(BlockGasPrices(height = block.height, gasPrices = block.txs.map {
        tx =>
          Numeric.toBigInt(formatHex(formatHex(tx.gasPrice))).longValue()
      }))
    }
}
