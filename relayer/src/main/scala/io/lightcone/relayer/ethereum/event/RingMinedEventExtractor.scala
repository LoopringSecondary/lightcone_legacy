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

package io.lightcone.relayer.ethereum.event

import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.event.{RingMinedEvent => PRingMinedEvent, _}
import io.lightcone.relayer.data._
import org.web3j.utils.Numeric
import scala.concurrent._
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.ethereum._

class RingMinedEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    config: Config,
    metadataManager: MetadataManager,
    rawOrderValidator: RawOrderValidator)
    extends EventExtractor[PRingMinedEvent] {

  val ringSubmitterAddress =
    Address(config.getString("loopring_protocol.protocol-address")).toString()

  implicit val ringBatchContext = RingBatchContext(
    lrcAddress = metadataManager.getTokenWithSymbol("lrc").get.meta.address
  )
  val fillLength: Int = 8 * 64

  def extract(block: RawBlockData): Future[Seq[PRingMinedEvent]] = Future {
    var rings = (block.txs zip block.receipts).flatMap {
      case (tx, receipt) if tx.to.equalsIgnoreCase(ringSubmitterAddress) =>
        val header = getEventHeader(tx, receipt, block.timestamp)
        receipt.logs.zipWithIndex.map {
          case (log, index) =>
            loopringProtocolAbi
              .unpackEvent(log.data, log.topics.toArray) match {
              case Some(event: RingMinedEvent.Result) =>
                val fillContent = Numeric.cleanHexPrefix(event._fills)
                val fillStrs = (0 until (fillContent.length / fillLength)).map {
                  index =>
                    fillContent.substring(
                      index * fillLength,
                      fillLength * (index + 1)
                    )
                }
                val orderFilledEvents = fillStrs.zipWithIndex.map {
                  case (fill, eventIndex) =>
                    val _fill =
                      if (eventIndex + 1 >= fillStrs.size) fillStrs.head
                      else fillStrs(eventIndex + 1)
                    fillToOrderFilledEvent(
                      fill,
                      _fill,
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
                    ringIndex = event._ringIndex.longValue,
                    ringHash = event._ringHash,
                    fills = orderFilledEvents
                  )
                )
              case _ =>
                None
            }
        }.filter(_.nonEmpty).map(_.get)
      case _ => Seq.empty
    }

    val ringBatches: Map[String, RingBatch] =
      (block.txs zip block.receipts).map {
        case (tx, receipt) =>
          ringSubmitterAbi.unpackFunctionInput(tx.input) match {
            case Some(params: SubmitRingsFunction.Params) =>
              val ringData = params.data
              new SimpleRingBatchDeserializer(Numeric.toHexString(ringData)).deserialize match {
                case Left(_) =>
                  None
                case Right(ringBatch) =>
                  if (!isSucceed(receipt.status)) {
                    val header = getEventHeader(tx, receipt, block.timestamp)
                    rings = rings.++(ringBatch.rings.map { ring =>
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
                    })
                  }
                  Some(tx.hash -> ringBatch)
              }
            case _ =>
              None
          }
      }.filter(_.isDefined).map(_.get).toMap

    rings.map { ring =>
      if (ring.header.get.txStatus.isTxStatusSuccess) {
        val ringBatch = ringBatches(ring.getHeader.txHash)
        val fills = ring.fills.map { fill =>
          val order = ringBatch.orders
            .find(order => fill.orderHash.equalsIgnoreCase(order.hash))
            .get
          fill.copy(
            waiveFeePercentage = order.getFeeParams.waiveFeePercentage,
            walletSplitPercentage = order.getFeeParams.walletSplitPercentage,
            tokenFee = Address.normalize(order.feeParams.get.tokenFee),
            wallet = Address.normalize(order.getParams.wallet)
          )
        }
        ring.copy(
          fills = fills,
          miner = Address.normalize(ringBatch.miner),
          feeRecipient = Address.normalize(ringBatch.feeRecipient)
        )
      } else {
        ring
      }
    }
  }

  private def fillToOrderFilledEvent(
      fill: String,
      _fill: String,
      event: RingMinedEvent.Result,
      receipt: TransactionReceipt,
      header: Option[EventHeader]
    ): OrderFilledEvent = {
    val data = Numeric.cleanHexPrefix(fill)
    val _data = Numeric.cleanHexPrefix(_fill)
    OrderFilledEvent(
      header,
      orderHash = Numeric.prependHexPrefix(data.substring(0, 64 * 1)),
      owner = Address.normalize(data.substring(64 * 1, 64 * 2)),
      tokenS = Address.normalize(data.substring(64 * 2, 64 * 3)),
      tokenB = Address.normalize(_data.substring(64 * 2, 64 * 3)),
      ringHash = event._ringHash,
      ringIndex = event._ringIndex.longValue,
      fillIndex = header.get.eventIndex,
      filledAmountS = NumericConversion.toBigInt(data.substring(64 * 3, 64 * 4)),
      filledAmountB =
        NumericConversion.toBigInt(_data.substring(64 * 3, 64 * 4)),
      split = BigInt(Numeric.toBigInt(data.substring(64 * 4, 64 * 5))),
      filledAmountFee = NumericConversion
        .toBigInt(data.substring(64 * 5, 64 * 6)),
      feeAmountS = NumericConversion
        .toBigInt(data.substring(64 * 6, 64 * 7)),
      feeAmountB = NumericConversion
        .toBigInt(data.substring(64 * 7, 64 * 8))
    )
  }

}
