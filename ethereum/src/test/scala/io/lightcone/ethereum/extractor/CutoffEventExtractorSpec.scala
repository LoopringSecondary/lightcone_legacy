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

import io.lightcone.ethereum.BlockHeader
import io.lightcone.ethereum.TxStatus.{TX_STATUS_FAILED, TX_STATUS_SUCCESS}
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.event._
import io.lightcone.ethereum.persistence.Activity.{
  ActivityType,
  OrderCancellation
}
import io.lightcone.ethereum.persistence._

import scala.concurrent.Await
import scala.concurrent.duration._

class CutoffEventExtractorSpec extends AbstractExtractorSpec {

  "extract block contains OrdersCancellEvent" should "get events correctly" in {
    import scala.concurrent.ExecutionContext.Implicits.global

    val cutoffEventsExtractor = new TxCutoffEventExtractor()
    val transactions = getTransactionDatas(
      "ethereum/src/test/resources/event/cutoff/cancel_orders_block"
    )
    val tx = transactions(0)
    loopringProtocolAbi.unpackEvent(
      tx.receiptAndHeaderOpt.get._1.logs(0).data,
      tx.receiptAndHeaderOpt.get._1.logs(0).topics.toArray
    )
    info(
      "extracted events should contains OrdersCancelledOnChainEvent and Activity if tx.status = TX_STATUS_SUCCESS"
    )
    val successEvents = Await.result(
      cutoffEventsExtractor.extractEvents(tx),
      5.second
    )

    val orderHashes = Seq(
      "0x1f4b506f995c07302cb09b278a877562c45c109f5151f78e3a7a01f6b5571340",
      "0x1f4b506f995c07302cb09b278a877562c45c109f5151f78e3a7a01f6b5571341",
      "0x1f4b506f995c07302cb09b278a877562c45c109f5151f78e3a7a01f6b5571342"
    )
    val event = OrdersCancelledOnChainEvent(
      header = Some(
        EventHeader(
          txHash =
            "0x0f2c24a0596b1e1fc279b7b043652379b987dab3add15be392787560706851db",
          txStatus = TX_STATUS_SUCCESS,
          blockHeader = Some(
            BlockHeader(
              height = 45,
              hash =
                "0x5daa842b7ce3aee6e25871ff851b6b5a0f469d023ef432cef1bbd36bde357d33",
              miner = "0x0000000000000000000000000000000000000000",
              timestamp = 1551768613
            )
          )
        )
      ),
      owner = "0xe20cf871f1646d8651ee9dc95aab1d93160b3467",
      broker = "0xe20cf871f1646d8651ee9dc95aab1d93160b3467",
      orderHashes = orderHashes
    )
    successEvents.find { e =>
      e.isInstanceOf[OrdersCancelledOnChainEvent]
    }.get
      .asInstanceOf[OrdersCancelledOnChainEvent] should be(event)

    val activity = Activity(
      owner = "0xe20cf871f1646d8651ee9dc95aab1d93160b3467",
      block = 45,
      txHash =
        "0x0f2c24a0596b1e1fc279b7b043652379b987dab3add15be392787560706851db",
      activityType = ActivityType.ORDER_CANCEL,
      timestamp = 1551768613,
      token = "0x0000000000000000000000000000000000000000", //token为0 会放置在eth中
      txStatus = TX_STATUS_SUCCESS,
      detail = Activity.Detail.OrderCancellation(
        OrderCancellation(
          orderIds = orderHashes,
          broker = "0xe20cf871f1646d8651ee9dc95aab1d93160b3467"
        )
      )
    )
    successEvents.find { e =>
      e.isInstanceOf[Activity]
    }.get
      .asInstanceOf[Activity] should be(activity)

    info(
      "extracted events should contains OrdersCancelledOnChainEvent and Activity if tx.status = TX_STATUS_FAILED"
    )
    val failedTxData = tx.copy(
      receiptAndHeaderOpt = Some(
        tx.receiptAndHeaderOpt.get._1.copy(status = TX_STATUS_FAILED),
        tx.receiptAndHeaderOpt.get._2
      )
    )
    val failedEvents = Await.result(
      cutoffEventsExtractor.extractEvents(failedTxData),
      5.second
    )
    failedEvents.exists(_.isInstanceOf[Activity]) should be(true)
    failedEvents.forall(!_.isInstanceOf[OrdersCancelledOnChainEvent]) should be(
      true
    )
    info(s"${failedEvents}")

    info(
      "extracted events should contains OrdersCancelledOnChainEvent and Activity if tx.status = TX_STATUS_PENDING"
    )
    val pendingTxData = tx.copy(receiptAndHeaderOpt = None)
    val pendingEvents = Await.result(
      cutoffEventsExtractor.extractEvents(pendingTxData),
      5.second
    )
    pendingEvents.exists(_.isInstanceOf[Activity]) should be(true)
    pendingEvents.forall(!_.isInstanceOf[OrdersCancelledOnChainEvent]) should be(
      true
    )
    val pendingActivity = pendingEvents
      .find(_.isInstanceOf[Activity])
      .get
      .asInstanceOf[Activity]
    pendingActivity.getOrderCancellation.orderIds.diff(orderHashes) should be(
      Seq.empty
    )
    info(s"${pendingEvents}")
  }

  "extract block contains CutOffEvents" should "get events correctly" in {
    import scala.concurrent.ExecutionContext.Implicits.global

    val cutoffEventsExtractor = new TxCutoffEventExtractor()
    val transactions = getTransactionDatas(
      "ethereum/src/test/resources/event/cutoff/cancel_for_trading_pair"
    )
    val tx = transactions(0)
    loopringProtocolAbi.unpackEvent(
      tx.receiptAndHeaderOpt.get._1.logs(0).data,
      tx.receiptAndHeaderOpt.get._1.logs(0).topics.toArray
    )
    val events = Await.result(
      cutoffEventsExtractor.extractEvents(tx),
      5.second
    )
    val txHash =
      "0x60cf2c444eb2759ec2a3ffeee8dc7b69b6f7e28a0f86881a9082a054f015a2ed"
    val event = CutoffEvent(
      header = Some(
        EventHeader(
          txHash = txHash,
          txStatus = TX_STATUS_SUCCESS,
          blockHeader = Some(
            BlockHeader(
              height = 45,
              hash =
                "0xc6bc536ee8cf01bd80973d8730db8321d149497370fb9aad329e9bb39f887839",
              miner = "0x0000000000000000000000000000000000000000",
              timestamp = 1551770861
            )
          )
        )
      ),
      owner = "0xe20cf871f1646d8651ee9dc95aab1d93160b3467",
      broker = "0xe20cf871f1646d8651ee9dc95aab1d93160b3467",
      cutoff = BigInt(10000000).longValue(),
      marketHash = "0xeb9187f4734e15e5f04058f64b9b8194781c0afa"
    )
    events.find { e =>
      e.isInstanceOf[CutoffEvent]
    }.get
      .asInstanceOf[CutoffEvent] should be(event)
    val activity = Activity(
      owner = "0xe20cf871f1646d8651ee9dc95aab1d93160b3467",
      block = 45,
      txHash = txHash,
      activityType = ActivityType.ORDER_CANCEL,
      timestamp = 1551770861,
      token = "0x0000000000000000000000000000000000000000", //token为0 会放置在eth中
      txStatus = TX_STATUS_SUCCESS,
      detail = Activity.Detail.OrderCancellation(
        OrderCancellation(
          cutoff = BigInt(10000000).longValue(),
          broker = "0xe20cf871f1646d8651ee9dc95aab1d93160b3467",
          marketPair = "0xeb9187f4734e15e5f04058f64b9b8194781c0afa"
        )
      )
    )
    events.find { e =>
      e.isInstanceOf[Activity]
    }.get
      .asInstanceOf[Activity] should be(activity)
  }

}
