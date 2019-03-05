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
import io.lightcone.ethereum.event._
import io.lightcone.ethereum.extractor.tx.TxApprovalEventExtractor
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.data.BlockWithTxObject

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.concurrent.duration._

class ApprovalEventExtractorSpec extends AbstractExtractorSpec {

  "extract approval event" should "extract all approval events and activities correctly" in {
    val resStr: String = Source
      .fromFile("ethereum/src/test/resources/event/approval_block")
      .getLines()
      .next()

    val source = deserializeToProto[BlockWithTxObject](resStr).get

    implicit val ec = Implicits.global

    val approvalExtractor =
      new TxApprovalEventExtractor()

    val blockHeader = BlockHeader(
      height = NumericConversion.toBigInt(source.getNumber.value).toLong,
      hash = source.hash,
      miner = source.miner,
      timestamp = NumericConversion.toBigInt(source.getTimestamp).toLong,
      uncles = source.uncleMiners
    )

    val result = for {
      events <- Future
        .sequence((source.transactions zip source.receipts).map {
          case (tx, receipt) =>
            val eventHeader = EventHeader(
              blockHeader = Some(blockHeader),
              txHash = tx.hash,
              txFrom = tx.from,
              txTo = tx.to,
              txStatus = receipt.status,
              txValue = tx.value
            )
            approvalExtractor
              .extractEvents(
                TransactionData(tx, Some(receipt -> eventHeader))
              )
        })
        .map(_.flatten)
      (approvals, activities) = events.partition(_.isInstanceOf[ApprovalEvent])
    } yield {
      approvals.size should be(5)
      activities.size should be(2)
    }
    Await.result(result, 60 second)
  }
}
