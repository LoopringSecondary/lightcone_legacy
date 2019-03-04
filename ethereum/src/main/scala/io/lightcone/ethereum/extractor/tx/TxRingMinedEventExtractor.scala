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

import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.core.MarketMetadata.Status.{ACTIVE, READONLY}
import io.lightcone.core.{
  MarketHash,
  MarketMetadata,
  MarketPair,
  MetadataManager,
  Token
}
import io.lightcone.ethereum.BlockHeader
import io.lightcone.ethereum.abi.{RingMinedEvent, _}
import io.lightcone.ethereum.event.{
  EventHeader,
  RingMinedEvent => PRingMinedEvent
}
import io.lightcone.ethereum.persistence.Activity.ActivityType
import io.lightcone.ethereum.persistence._
import io.lightcone.ethereum.persistence.Fill.Fee
import io.lightcone.lib._
import io.lightcone.relayer.data.{OHLCData, TransactionReceipt}
import org.web3j.utils._

import scala.concurrent._

class TxRingMinedEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    val config: Config,
    val metadataManager: MetadataManager)
    extends EventExtractor[TransactionData, Any] {

  val ringSubmitterAddress =
    Address(config.getString("loopring_protocol.protocol-address")).toString()

  val fillLength: Int = 8 * 64

  //TODO(hongyu):pending的需要如何处理，主要是p2p前端的表现形式
  def extractEvents(txdata: TransactionData) = Future {
    if (!txdata.tx.to.equalsIgnoreCase(ringSubmitterAddress)) {
      Seq.empty
    } else {
      txdata.receiptAndHeaderOpt match {
        case Some((receipt, eventHeader)) =>
          receipt.logs.zipWithIndex.map {
            case (log, index) =>
              loopringProtocolAbi
                .unpackEvent(log.data, log.topics.toArray) match {
                case Some(event: RingMinedEvent.Result) =>
                  val fillContent = Numeric.cleanHexPrefix(event._fills)
                  val fillStrs =
                    (0 until (fillContent.length / fillLength)).map { index =>
                      fillContent.substring(
                        index * fillLength,
                        fillLength * (index + 1)
                      )
                    }
                  val fills = deserializeFill(
                    fillStrs,
                    event,
                    txdata.tx.hash,
                    eventHeader.getBlockHeader
                  )
                  val ringMinedEvents =
                    generateRingMinedEvents(fills, eventHeader)
                  val ohlcDatas = generateOHLCDatas(
                    fills,
                    event._ringIndex.longValue(),
                    eventHeader
                  )
                  val activities = genereateActivity(fills, eventHeader)
                  fills ++ ringMinedEvents ++ ohlcDatas ++ activities
                case _ =>
                  Seq.empty
              }
          }
        case None =>
          //TODO:Pending的如何处理
          Seq.empty
      }
    }
  }

  private def generateRingMinedEvents(
      fills: Seq[Fill],
      eventHeader: EventHeader
    ): Seq[PRingMinedEvent] = {
    fills.zipWithIndex map {
      case (fill, index) =>
        val nextFill =
          if (index + 1 >= fills.size) fills.head else fills(index + 1)
        PRingMinedEvent(
          header = Some(eventHeader),
          orderIds = Seq(fill.orderHash, nextFill.orderHash),
          marketPair = Some(MarketPair(fill.tokenS, nextFill.tokenS))
        )
    }
  }

  private def generateOHLCDatas(
      fills: Seq[Fill],
      ringIndex: Long,
      eventHeader: EventHeader
    ): Seq[OHLCRawData] = {
    val ohlcDatas = fills.zipWithIndex map {
      case (fill, index) =>
        val nextFill =
          if (index + 1 >= fills.size) fills.head else fills(index + 1)

        val marketHash =
          MarketHash(MarketPair(fill.tokenS, nextFill.tokenB)).toString

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
            OHLCRawData(
              ringIndex = ringIndex,
              txHash = eventHeader.txHash,
              marketHash = marketHash,
              time = eventHeader.getBlockHeader.timestamp,
              baseAmount = baseAmount,
              quoteAmount = quoteAmount,
              price = BigDecimal(quoteAmount / baseAmount)
                .setScale(marketMetadata.priceDecimals)
                .doubleValue()
            )
          )
        }
    }
    ohlcDatas.filterNot(_.isEmpty).map(_.get)
  }

  private def getAmounts(
      fill: Fill,
      baseToken: Token,
      quoteToken: Token,
      marketMetadata: MarketMetadata
    ): (Double, Double) = {
    val amountInWei =
      if (Address(
            baseToken.getAddress()
          ).equals(Address(fill.tokenS)))
        Numeric.toBigInt(fill.amountS.toByteArray)
      else Numeric.toBigInt(fill.amountB.toByteArray)

    val amount: Double = quoteToken
      .fromWei(amountInWei, marketMetadata.precisionForAmount)
      .doubleValue()

    val totalInWei =
      if (Address(
            quoteToken.getAddress()
          ).equals(Address(fill.tokenS)))
        Numeric.toBigInt(fill.amountS.toByteArray)
      else Numeric.toBigInt(fill.amountB.toByteArray)

    val total: Double = baseToken
      .fromWei(totalInWei, marketMetadata.precisionForTotal)
      .doubleValue()

    amount -> total
  }

  //generate two activity by one fill
  private def genereateActivity(
      fills: Seq[Fill],
      evethHeader: EventHeader
    ): Seq[Activity] = {
    val activities = fills.zipWithIndex.map {
      case (fill, index) =>
        val nextFill =
          if (index + 1 >= fills.size) fills.head else fills(index + 1)
        val isP2P = fill.owner == evethHeader.txFrom || nextFill.owner == evethHeader.txFrom

        //TODO(hongyu):价格等如何计算，是按照对应的计算，还是按照市场计算？包括tokenBase和tokenQuote
        val trade = Activity.Trade(
          address = fill.owner,
          tokenBase = fill.tokenS,
          tokenQuote = fill.tokenB,
          amountBase = fill.amountS,
          amountQuote = fill.amountB,
          isP2P = isP2P
//          price = fill.
        )
        val activity = Activity(
          owner = fill.owner,
          block = evethHeader.getBlockHeader.height,
          txHash = evethHeader.txHash,
          txStatus = evethHeader.txStatus,
          activityType = ActivityType.TRADE_BUY, //TODO:
          timestamp = evethHeader.getBlockHeader.timestamp,
          token = fill.tokenS,
          detail = Activity.Detail.Trade(trade)
        )
        Seq(
          activity,
          activity
            .copy(activityType = ActivityType.TRADE_SELL, token = fill.tokenB)
        )
    }
    activities.flatten
  }

  private def deserializeFill(
      fillData: Seq[String],
      eventRes: RingMinedEvent.Result,
      txHash: String,
      blockHeader: BlockHeader
    ): Seq[Fill] = {
    fillData.zipWithIndex map {
      case (fill, index) =>
        val nextFill =
          if (index + 1 >= fillData.size) fillData.head else fillData(index + 1)
        val data = Numeric.cleanHexPrefix(fill)
        val nextFillData = Numeric.cleanHexPrefix(nextFill)
        Fill(
          owner = Address.normalize(data.substring(64 * 1, 64 * 2)),
          orderHash = Numeric.prependHexPrefix(data.substring(0, 64 * 1)),
          ringHash = eventRes._ringHash,
          ringIndex = eventRes._ringIndex.longValue(),
          fillIndex = index,
          amountS = NumericConversion.toBigInt(data.substring(64 * 3, 64 * 4)),
          amountB =
            NumericConversion.toBigInt(nextFillData.substring(64 * 3, 64 * 4)),
          tokenS = Address.normalize(data.substring(64 * 2, 64 * 3)),
          tokenB = Address.normalize(nextFillData.substring(64 * 2, 64 * 3)),
          split = BigInt(Numeric.toBigInt(data.substring(64 * 4, 64 * 5))),
          fee = Some(Fee()), //TODO:补全
          //      wallet =
          miner = blockHeader.miner,
          blockHeight = blockHeader.height,
          blockTimestamp = blockHeader.timestamp
        )
    }
  }

}
