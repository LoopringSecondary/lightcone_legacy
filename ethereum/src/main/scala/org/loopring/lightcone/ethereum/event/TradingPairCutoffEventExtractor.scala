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

package org.loopring.lightcone.ethereum.event

import org.loopring.lightcone.ethereum.abi.AllOrdersCancelledForTradingPairEvent
import org.loopring.lightcone.lib.MarketHashProvider.convert2Hex
import org.loopring.lightcone.proto._

class TradingPairCutoffEventExtractor()
    extends DataExtractor[TradingPairCutoffEvent] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[TradingPairCutoffEvent] = {
    receipt.logs.map { log ⇒
      loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
        case Some(event: AllOrdersCancelledForTradingPairEvent.Result) ⇒
          Some(
            TradingPairCutoffEvent(
              header = Some(getEventHeader(tx, receipt, blockTime)),
              cutoff = event._cutoff.longValue(),
              broker = event._broker,
              tradingPair = convert2Hex(event._token1, event._token2)
            )
          )
        case _ ⇒
          None
      }
    }.filter(_.nonEmpty).map(_.get)
  }
}
