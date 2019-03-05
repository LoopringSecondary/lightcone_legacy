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

import io.lightcone.core.MarketPair
import io.lightcone.ethereum.TxStatus._
import io.lightcone.ethereum._
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.event.{RingMinedEvent => PRingMinedEvent, _}
import io.lightcone.ethereum.persistence._

import scala.concurrent.Await
import scala.concurrent.duration._

class RingMinedEventExtractorSpec extends AbstractExtractorSpec {

  "extract a block contains SubmitRing" should "get events correctly" in {
    import scala.concurrent.ExecutionContext.Implicits.global

    val ringMinedEventExtractor = new TxRingMinedEventExtractor()
    val transactions =
      getTransactionDatas("ethereum/src/test/resources/event/ring_mined_block")
    val successTxData = transactions(0)
    loopringProtocolAbi.unpackEvent(
      successTxData.receiptAndHeaderOpt.get._1.logs(0).data,
      successTxData.receiptAndHeaderOpt.get._1.logs(0).topics.toArray
    )
    val successEvents = Await.result(
      ringMinedEventExtractor.extractEvents(successTxData),
      5.second
    )

    val eventHeader = EventHeader(
      txHash =
        "0x5839f2202220b6f3a4d88f15db5220ccefc645205636c38aa19ad452980f1e71",
      txStatus = TX_STATUS_SUCCESS,
      blockHeader = Some(
        BlockHeader(
          height = 48,
          hash =
            "0x1f4b506f995c07302cb09b278a877562c45c109f5151f78e3a7a01f6b5571346",
          miner = "0x0000000000000000000000000000000000000000",
          timestamp = 1551699406
        )
      )
    )

    val expectedRingMinedEvent = PRingMinedEvent(
      header = Some(eventHeader),
      orderIds = Seq(
        "0xf94046d0b5e7b5a18d05d005151af54be7f653fea9df64457845bc71dd6abbce",
        "0xe4d3e769b941a0698928825e932901fadeebc11c0f1f43f149c30b7f6c236c16"
      )
    )

    successEvents.count(_.isInstanceOf[Fill]) should be(2)
    successEvents.count(_.isInstanceOf[Activity]) should be(4)
    successEvents.count(_.isInstanceOf[PRingMinedEvent]) should be(1)
    successEvents.count(_.isInstanceOf[OHLCRawData]) should be(2)

    val ringMinedEvent = successEvents
      .find(_.isInstanceOf[PRingMinedEvent])
      .get
      .asInstanceOf[PRingMinedEvent]

    val marketPairs = Seq(
      MarketPair(LRC_TOKEN.address, WETH_TOKEN.address),
      MarketPair(WETH_TOKEN.address, LRC_TOKEN.address)
    )
    marketPairs.contains(ringMinedEvent.getMarketPair) should be(true)
    ringMinedEvent.copy(marketPair = None) should be(expectedRingMinedEvent)

    info(
      "extracted events should not contains events excepts Activity if tx.status = TX_STATUS_FAILED"
    )
    val failedTxData = successTxData.copy(
      receiptAndHeaderOpt = Some(
        successTxData.receiptAndHeaderOpt.get._1
          .copy(status = TX_STATUS_FAILED),
        successTxData.receiptAndHeaderOpt.get._2
      )
    )
    //TODO(hongyu):解析订单的可能有bug，需要与孔亮确认，暂不做验证

//    val failedEvents = Await.result(
//      ringMinedEventExtractor.extractEvents(failedTxData),
//      5.second
//    )
//    failedEvents.forall { e =>
//      e.isInstanceOf[Activity] || e.isInstanceOf[PRingMinedEvent]
//    } should be(true)

    info(
      "extracted events should not contains events excepts Activity if tx.status = TX_STATUS_PENDING"
    )
    val pendingTxData = successTxData.copy(receiptAndHeaderOpt = None)
//    val pendingEvents = Await.result(
//      ringMinedEventExtractor.extractEvents(pendingTxData),
//      5.second
//    )
//    pendingEvents
//      .forall(_.isInstanceOf[Activity]) should be(true)
  }
}
