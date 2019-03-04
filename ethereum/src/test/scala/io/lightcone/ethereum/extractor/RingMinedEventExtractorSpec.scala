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

import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.BlockHeader
import io.lightcone.ethereum.TxStatus._
import io.lightcone.ethereum.event.EventHeader
import io.lightcone.ethereum.event.{RingMinedEvent => PRingMinedEvent, _}
import io.lightcone.ethereum.persistence._
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.data._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

class RingMinedEventExtractorSpec extends AbstractExtractorSpec {

  "extract block" should "get events correctly" in {
    val resStr: String = Source
      .fromFile("ethereum/src/test/resources/event/mined_block")
      .getLines()
      .next()

    val block = deserializeToProto[BlockWithTxObject](resStr).get

    import scala.concurrent.ExecutionContext.Implicits.global

    val ringMinedEventExtractor = new TxRingMinedEventExtractor()
    val transactions = (block.transactions zip block.receipts).map {
      case (tx, receipt) =>
        val eventHeader =
          EventHeader(
            txHash = tx.hash,
            txStatus = TX_STATUS_SUCCESS,
            blockHeader = Some(
              BlockHeader(
                NumericConversion.toBigInt(block.number).longValue(),
                block.hash,
                block.miner,
                NumericConversion.toBigInt(block.timestamp).longValue(),
                block.uncles
              )
            )
          )
        TransactionData(tx, Some(receipt, eventHeader))
    }
    val tx = transactions(0)
    loopringProtocolAbi.unpackEvent(
      tx.receiptAndHeaderOpt.get._1.logs(0).data,
      tx.receiptAndHeaderOpt.get._1.logs(0).topics.toArray
    )
    val events = Await.result(
      ringMinedEventExtractor.extractEvents(tx),
      5.second
    )
    val fills = events.filter(_ match {
      case _: Fill => true
      case _       => false
    })
    fills.nonEmpty should be(true)
    val activities = events.filter(_ match {
      case _: Activity => true
      case _           => false
    })
    activities.nonEmpty should be(true)

    val ringMinedEvents = events.filter(_ match {
      case _: PRingMinedEvent => true
      case _                  => false
    })
    ringMinedEvents.nonEmpty should be(true)

    val ohlcDatas = events.filter(_ match {
      case _: OHLCRawData => true
      case _              => false
    })
    ohlcDatas.nonEmpty should be(true)
  }
}
