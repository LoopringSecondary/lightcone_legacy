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

package org.loopring.lightcone.actors.ethereum.event

import com.google.inject.Inject
import org.loopring.lightcone.core.base.MetadataManager
import org.loopring.lightcone.proto.{RawBlockData, Ring}

import scala.concurrent.{ExecutionContext, Future}

class RingExtractor @Inject()(
    implicit
    extractor: TradeExtractor,
    metadataManager: MetadataManager,
    val ec: ExecutionContext)
    extends EventExtractor[Ring] {

  def extract(block: RawBlockData): Future[Seq[Ring]] = {
    extractor.extract(block).map { trades =>
      trades
        .groupBy(trade => trade.ringHash + trade.ringIndex)
        .values
        .map { trades =>
          val head = trades.head
          Ring(
            ringHash = head.ringHash,
            ringIndex = head.ringIndex,
            fillsAmount = trades.size,
            txHash = head.txHash,
            blockHeight = head.blockHeight,
            blockTimestamp = head.blockTimestamp,
            fees = Some(Ring.Fees(trades.map(_.getFee)))
          )
        }
        .toSeq
    }
  }
}
