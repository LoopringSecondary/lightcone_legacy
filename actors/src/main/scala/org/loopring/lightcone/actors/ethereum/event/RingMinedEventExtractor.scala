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
import com.typesafe.config.Config
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data._
import org.loopring.lightcone.proto.{RingMinedEvent => PRingMinedEvent, _}
import org.web3j.utils.Numeric
import org.loopring.lightcone.lib.data._

import scala.concurrent._
import org.loopring.lightcone.core.base.MetadataManager
import org.loopring.lightcone.ethereum.SimpleRingBatchDeserializer

class RingMinedEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    config: Config,
    metadataManager: MetadataManager)
    extends EventExtractor[PRingMinedEvent] {

  val ringSubmitterAddress =
    Address(config.getString("loopring_protocol.protocol-address")).toString()

  implicit val ringBatchContext = RingBatchContext(
    lrcAddress = metadataManager.getTokenBySymbol("lrc").get.meta.address
  )
  val fillLength: Int = 8 * 64

  def extract(block: RawBlockData): Future[Seq[PRingMinedEvent]] = Future {
    (block.txs zip block.receipts).flatMap {
      case (tx, receipt) if tx.to.equalsIgnoreCase(ringSubmitterAddress) =>
        val header = getEventHeader(tx, receipt, block.timestamp)
        if (isSucceed(receipt.status)) {
          receipt.logs.zipWithIndex.map {
            case (log, index) =>
              loopringProtocolAbi
                .unpackEvent(log.data, log.topics.toArray) match {
                case Some(event: RingMinedEvent.Result) =>
                  val fillContent = Numeric.cleanHexPrefix(event._fills)
                  val orderFilledEvents =
                    (0 until (fillContent.length / fillLength)).map { index =>
                      fillContent.substring(
                        index * fillLength,
                        fillLength * (index + 1)
                      ) -> index
                    }.map {
                      case (fill, eventIndex) =>
                        fillToOrderFilledEvent(
                          fill,
                          event,
                          receipt,
                          Some(
                            header
                              .copy(logIndex = index, eventIndex = eventIndex)
                          )
                        )
                    }
                  Some(
                    PRingMinedEvent(
                      header = Some(header.withLogIndex(index)),
                      ringIndex = event._ringIndex.longValue(),
                      ringHash = event._ringHash,
                      fills = orderFilledEvents
                    )
                  )
                case _ =>
                  None
              }
          }.filter(_.nonEmpty).map(_.get)
        } else {
          ringSubmitterAbi.unpackFunctionInput(tx.input) match {
            case Some(params: SubmitRingsFunction.Params) =>
              val ringData = params.data
              new SimpleRingBatchDeserializer(Numeric.toHexString(ringData)).deserialize match {
                case Left(_) =>
                  Seq.empty
                case Right(ringBatch) =>
                  ringBatch.rings.map { ring =>
                    PRingMinedEvent(
                      header = Some(header),
                      fills = ring.orderIndexes.map(index => {
                        val order = ringBatch.orders(index)
                        OrderFilledEvent(
                          header = Some(header),
                          orderHash = order.hash,
                          tokenS = Address.normalize(order.tokenS)
                        )
                      })
                    )
                  }
              }
            case _ =>
              Seq.empty
          }
        }
      case _ => Seq.empty
    }
  }
  private def fillToOrderFilledEvent(
      fill: String,
      event: RingMinedEvent.Result,
      receipt: TransactionReceipt,
      header: Option[EventHeader]
    ): OrderFilledEvent = {
    val data = Numeric.cleanHexPrefix(fill)
    OrderFilledEvent(
      header,
      orderHash = Numeric.prependHexPrefix(data.substring(0, 64 * 1)),
      owner = Address.normalize(data.substring(64 * 1, 64 * 2)),
      tokenS = Address.normalize(fill.substring(64 * 2, 64 * 3)),
      ringHash = event._ringHash,
      ringIndex = event._ringIndex.longValue(),
      filledAmountS = BigInt(Numeric.toBigInt(data.substring(64 * 3, 64 * 4))),
      split = BigInt(Numeric.toBigInt(data.substring(64 * 4, 64 * 5))),
      filledAmountFee = BigInt(
        Numeric
          .toBigInt(data.substring(64 * 5, 64 * 6))
      ),
      feeAmountS = BigInt(
        Numeric
          .toBigInt(data.substring(64 * 6, 64 * 7))
      ),
      feeAmountB = BigInt(
        Numeric
          .toBigInt(data.substring(64 * 7, 64 * 8))
      ),
      feeRecipient = Address.normalize(event._feeRecipient)
    )
  }

}
