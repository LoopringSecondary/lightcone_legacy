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

package io.lightcone.ethereum.extractor.block

import akka.actor.ActorRef
import com.google.inject.Inject
import io.lightcone.ethereum.BlockHeader
import io.lightcone.ethereum.event._
import io.lightcone.ethereum.extractor.tx.TxTransferEventExtractor
import io.lightcone.ethereum.extractor._
import io.lightcone.lib._
import io.lightcone.relayer.data.BlockWithTxObject

import scala.concurrent.{ExecutionContext, Future}

class BalanceUpdateAddressExtractor @Inject(getActorRef:String => ActorRef)(
    implicit
    val ec: ExecutionContext,
    val event: TxTransferEventExtractor)
    extends EventExtractor[BlockWithTxObject, AddressBalanceUpdatedEvent] {

  def extractEvents(
      source: BlockWithTxObject
    ): Future[Seq[AddressBalanceUpdatedEvent]] = {
    val blockHeader = BlockHeader(
      height = NumericConversion.toBigInt(source.getNumber.value).toLong,
      hash = source.hash,
      miner = source.miner,
      timestamp = NumericConversion.toBigInt(source.getTimestamp).toLong,
      uncles = source.uncleMiners
    )
    (source.transactions zip source.receipts).flatMap {
      case (tx, receipt) =>
        val eventHeader = EventHeader(
          blockHeader = Some(blockHeader),
          txHash = tx.hash,
          txFrom = tx.from,
          txTo = tx.to,
          txStatus = receipt.status,
          txValue = tx.value
        )
        val transferEvents = event.extractTransferEvents(
          TransactionData(tx, Some(receipt -> eventHeader))
        )
        val (ethAddresses, tokenAddresses) =
          transferEvents.partition(_.token == Address.ZERO.toString())




      case _ => Future.successful(Nil)
    }
  }
}
