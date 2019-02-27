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
import io.lightcone.core.MarketMetadata.Status.{ACTIVE, READONLY}
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
    metadataManagerArg: MetadataManager,
    rawOrderValidatorArg: RawOrderValidator)
    extends AbstractEventExtractor
    with RingMinedEventSupport
    with FillEventSupport
    with OHLCRawDataSupport {

  implicit val metadataManager = metadataManagerArg
  implicit val rawOrderValidator = rawOrderValidatorArg

  val ringSubmitterAddress =
    Address(config.getString("loopring_protocol.protocol-address")).toString()

  implicit def ringBatchContext = RingBatchContext(
    lrcAddress = metadataManager
      .getTokenWithSymbol("lrc")
      .getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found token: LRC"
        )
      )
      .getAddress()
  )
  val fillLength: Int = 8 * 64

  def extractEventsFromTx(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: EventHeader
    ): Future[Seq[AnyRef]] =
    for {
      ringMinedEvents <- extractRingMinedEvents(tx, receipt, eventHeader)
      fillEvents = extractFilledEvents(ringMinedEvents)
      ohlcRawDataEvents = extractOHLCRawData(ringMinedEvents)
    } yield ringMinedEvents ++ fillEvents ++ ohlcRawDataEvents

}

trait OHLCRawDataSupport {
  extractor: RingMinedEventExtractor =>

  def extractOHLCRawData(ringMinedEvents: Seq[PRingMinedEvent]) = {
    ringMinedEvents
      .filter(
        ring =>
          ring.header.isDefined && ring.getHeader.txStatus.isTxStatusSuccess
      )
      .flatMap { ring =>
        ring.fills.map { fill =>
          val marketHash =
            MarketHash(MarketPair(fill.tokenS, fill.tokenB)).toString

          if (!metadataManager.isMarketStatus(marketHash, ACTIVE, READONLY))
            None
          else {
            val marketMetadata =
              metadataManager.getMarket(marketHash)
            val marketPair = marketMetadata.getMarketPair
            val baseToken =
              metadataManager.getTokenWithAddress(marketPair.baseToken).get
            val quoteToken =
              metadataManager.getTokenWithAddress(marketPair.quoteToken).get
            val (baseAmount, quoteAmount) =
              getAmounts(fill, baseToken, quoteToken, marketMetadata)
            Some(
              OHLCRawDataEvent(
                ringIndex = ring.ringIndex,
                txHash = ring.header.get.txHash,
                marketHash = marketHash,
                time = ring.getHeader.getBlockHeader.timestamp,
                baseAmount = baseAmount,
                quoteAmount = quoteAmount,
                price = BigDecimal(quoteAmount / baseAmount)
                  .setScale(marketMetadata.priceDecimals)
                  .doubleValue()
              )
            )
          }
        }.filter(_.isDefined).map(_.get).distinct
      }
  }

  // LRC-WETH market, LRC is the base token, WETH is the quote token.
  private def getAmounts(
      fill: OrderFilledEvent,
      baseToken: Token,
      quoteToken: Token,
      marketMetadata: MarketMetadata
    ): (Double, Double) = {
    val amountInWei =
      if (Address(
            baseToken.getAddress()
          ).equals(Address(fill.tokenS)))
        Numeric.toBigInt(fill.filledAmountS.toByteArray)
      else Numeric.toBigInt(fill.filledAmountB.toByteArray)

    val amount: Double = quoteToken
      .fromWei(amountInWei, marketMetadata.precisionForAmount)
      .doubleValue()

    val totalInWei =
      if (Address(
            quoteToken.getAddress()
          ).equals(Address(fill.tokenS)))
        Numeric.toBigInt(fill.filledAmountS.toByteArray)
      else Numeric.toBigInt(fill.filledAmountB.toByteArray)

    val total: Double = baseToken
      .fromWei(totalInWei, marketMetadata.precisionForTotal)
      .doubleValue()

    amount -> total
  }
}

trait FillEventSupport {
  extractor: RingMinedEventExtractor =>

  def extractFilledEvents(ringMinedEvents: Seq[PRingMinedEvent]) = {
    ringMinedEvents
      .filter(_.getHeader.txStatus.isTxStatusSuccess)
      .flatMap(_.fills)
  }
}

trait RingMinedEventSupport {
  extractor: RingMinedEventExtractor =>

  def extractRingMinedEvents(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: EventHeader
    ): Future[Seq[PRingMinedEvent]] = Future {
    if (!tx.to.equalsIgnoreCase(ringSubmitterAddress)) {
      Seq.empty[PRingMinedEvent]
    } else {
      var rings = receipt.logs.zipWithIndex.map {
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
                      eventHeader
                        .copy(logIndex = index, eventIndex = eventIndex)
                    )
                  )
              }
              Some(
                PRingMinedEvent(
                  header = Some(eventHeader.withLogIndex(index)),
                  ringIndex = event._ringIndex.longValue,
                  ringHash = event._ringHash,
                  fills = orderFilledEvents
                )
              )
            case _ =>
              None
          }
      }.filter(_.nonEmpty).map(_.get)

      val ringBatches: Map[String, RingBatch] =
        ringSubmitterAbi.unpackFunctionInput(tx.input) match {
          case Some(params: SubmitRingsFunction.Params) =>
            val ringData = params.data
            new SimpleRingBatchDeserializer(Numeric.toHexString(ringData)).deserialize match {
              case Left(_) =>
                Map.empty
              case Right(ringBatch) =>
                if (!isSucceed(receipt.status)) {
                  rings = rings.++(ringBatch.rings.map { ring =>
                    PRingMinedEvent(
                      header = Some(eventHeader),
                      fills = ring.orderIndexes.map(index => {
                        val order = ringBatch.orders(index)
                        OrderFilledEvent(
                          header = Some(eventHeader),
                          orderHash = order.hash,
                          tokenS = Address.normalize(order.tokenS)
                        )
                      })
                    )
                  })
                }
                Map(tx.hash -> ringBatch)
            }
          case _ =>
            Map.empty
        }

      //TODO 失败的环路没有抽取出来
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
