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
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.lib.MarketHashProvider.convert2Hex
import org.loopring.lightcone.proto.{CutoffEvent, RawBlockData}

import scala.concurrent._

class CutoffEventExtractor @Inject()(implicit val ec: ExecutionContext)
    extends EventExtractor[CutoffEvent] {

  def extract(block: RawBlockData): Future[Seq[CutoffEvent]] = Future {
    (block.txs zip block.receipts).flatMap {
      case (tx, receipt) =>
        val header = getEventHeader(tx, receipt, block.timestamp)
        receipt.logs.zipWithIndex.map {
          case (log, index) =>
            loopringProtocolAbi
              .unpackEvent(log.data, log.topics.toArray) match {
              case Some(event: AllOrdersCancelledEvent.Result) =>
                Some(
                  CutoffEvent(
                    header = Some(header.withLogIndex(index)),
                    cutoff = event._cutoff.longValue(),
                    broker = event._broker,
                    owner = event._broker
                  )
                )
              case Some(event: AllOrdersCancelledByBrokerEvent.Result) =>
                Some(
                  CutoffEvent(
                    header = Some(header.withLogIndex(index)),
                    cutoff = event._cutoff.longValue(),
                    broker = event._broker,
                    owner = event._owner
                  )
                )
              case Some(
                  event: AllOrdersCancelledForTradingPairByBrokerEvent.Result
                  ) =>
                Some(
                  CutoffEvent(
                    header = Some(header.withLogIndex(index)),
                    cutoff = event._cutoff.longValue(),
                    broker = event._broker,
                    owner = event._owner,
                    tradingPair = convert2Hex(event._token1, event._token2)
                  )
                )
              case Some(event: AllOrdersCancelledForTradingPairEvent.Result) =>
                Some(
                  CutoffEvent(
                    header = Some(header.withLogIndex(index)),
                    cutoff = event._cutoff.longValue(),
                    broker = event._broker,
                    owner = event._broker,
                    tradingPair = convert2Hex(event._token1, event._token2)
                  )
                )
              case _ =>
                None
            }
        }.filter(_.nonEmpty).map(_.get)
    }
  }
}
