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
import io.lightcone.ethereum.event.{EventHeader, TransferEvent}
import io.lightcone.ethereum.extractor.tx.TxTransferEventExtractor
import io.lightcone.ethereum.persistence.Activity
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.data.BlockWithTxObject
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.{Await, Future}
import scala.io.Source

class TransferEventExtractorSepc extends AbstractExtractorSpec {

  "extract transfer event" should "extract all transfer events correctly" in {

    val resStr: String = Source
      .fromFile("ethereum/src/test/resources/event/transfer_block")
      .getLines()
      .next()

    val source = deserializeToProto[BlockWithTxObject](resStr).get

    implicit val ec = Implicits.global

    implicit val txTransferEventExtractor = new TxTransferEventExtractor()(
      ec,
      metadataManager,
      protocolAddress
    )

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
            txTransferEventExtractor
              .extractEvents(
                TransactionData(tx, Some(receipt -> eventHeader))
              )
        })
        .map(_.flatten)
      (transfers, activities) = events.partition(_.isInstanceOf[TransferEvent])
      ethTransfers = activities
        .asInstanceOf[Seq[Activity]]
        .filter(
          activity =>
            activity.activityType.isEtherTransferIn || activity.activityType.isEtherTransferOut
        )
      tokenTransfers = activities
        .asInstanceOf[Seq[Activity]]
        .filter(
          activity =>
            activity.activityType.isTokenTransferIn || activity.activityType.isTokenTransferOut
        )
      wrapAndUnwraps = activities
        .asInstanceOf[Seq[Activity]]
        .filter(
          activity =>
            activity.activityType.isEtherUnwrap || activity.activityType.isEtherWrap
        )

    } yield {
      transfers.size should be(18)
      ethTransfers.size should be(2)
      tokenTransfers.size should be(2)
      wrapAndUnwraps.size should be(4)
    }

    Await.result(result, 60 second)

  }
}
