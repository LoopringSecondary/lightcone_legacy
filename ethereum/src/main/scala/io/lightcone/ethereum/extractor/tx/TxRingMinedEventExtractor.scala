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
import io.lightcone.core.MarketMetadata.Status._
import io.lightcone.core._
import io.lightcone.ethereum.TxStatus._
import io.lightcone.ethereum._
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.event.{RingMinedEvent => PRingMinedEvent, _}
import io.lightcone.ethereum.persistence.Activity.ActivityType
import io.lightcone.ethereum.persistence.Fill.Fee
import io.lightcone.ethereum.persistence._
import io.lightcone.lib._
import io.lightcone.relayer.data.Transaction
import org.web3j.utils._

import scala.concurrent._

class TxRingMinedEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    val config: Config,
    val metadataManager: MetadataManager)
    extends EventExtractor[TransactionData, AnyRef] {

  implicit val orderValidator = new RawOrderValidatorImpl()

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

  def extractEvents(txdata: TransactionData) = Future {
    if (!txdata.tx.to.equalsIgnoreCase(ringSubmitterAddress)) {
      Seq.empty
    } else {
      txdata.receiptAndHeaderOpt match {
        case Some((receipt, eventHeader)) =>
          if (receipt.status == TX_STATUS_FAILED) {
            //失败的环路提交，只生成RingMinedEvent
            val fills = extractInputToFill(txdata.tx)
            val ringMinedEvents = generateRingMinedEvents(fills, eventHeader)
            //如果是p2p则需要生成activity
            val activities = if (isP2P(fills, eventHeader.txFrom)) {
              genereateActivity(fills, eventHeader)
            } else Seq.empty
            ringMinedEvents ++ activities
          } else {
            receipt.logs.zipWithIndex.flatMap {
              case (log, index) =>
                loopringProtocolAbi
                  .unpackEvent(log.data, log.topics.toArray) match {
                  case Some(event: RingMinedEvent.Result) =>
                    val fillContent = Numeric.cleanHexPrefix(event._fills)
                    val fillStrs =
                      fillContent.zipWithIndex
                        .groupBy(_._2 / fillLength)
                        .map {
                          case (_, chars) => chars.map(_._1).mkString
                        }
                    val fills = deserializeFill(
                      fillStrs.toSeq,
                      event,
                      txdata.tx,
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
          }
        case None =>
          //pending的环路提交不生成RingMinedEvent
          val eventHeader = EventHeader(
            txFrom = txdata.tx.from,
            txHash = txdata.tx.hash,
            txStatus = TX_STATUS_PENDING,
            txTo = txdata.tx.to,
            txValue = txdata.tx.value,
            blockHeader = Some(BlockHeader())
          )
          val fills = extractInputToFill(txdata.tx)
          //但是如果是p2p则需要生成activity
          val activities = if (isP2P(fills, eventHeader.txFrom)) {
            genereateActivity(fills, eventHeader)
          } else Seq.empty
          activities
      }
    }
  }

  private def generateRingMinedEvents(
      fills: Seq[Fill],
      eventHeader: EventHeader
    ): Seq[PRingMinedEvent] = {
    val fill = fills(0)
    val nextFill = fills(1)
    Seq(
      PRingMinedEvent(
        header = Some(eventHeader),
        orderIds = Seq(fill.orderHash, nextFill.orderHash),
        marketPair = Some(MarketPair(fill.tokenS, nextFill.tokenS))
      )
    )
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
          MarketHash(MarketPair(fill.tokenS, nextFill.tokenS)).toString

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
      if (Address(baseToken.getAddress()).equals(Address(fill.tokenS)))
        Numeric.toBigInt(fill.amountS.toByteArray)
      else Numeric.toBigInt(fill.amountB.toByteArray)

    val amount: Double = quoteToken
      .fromWei(amountInWei, marketMetadata.precisionForAmount)
      .doubleValue()

    val totalInWei =
      if (Address(quoteToken.getAddress()).equals(Address(fill.tokenS)))
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
      eventHeader: EventHeader
    ): Seq[Activity] = {
    val isP2PRes = isP2P(fills, eventHeader.txFrom)

    val activities = fills.zipWithIndex.map {
      case (fill, index) =>
        val nextFill =
          if (index + 1 >= fills.size) fills.head else fills(index + 1)

        //p2p订单并且是失败的环路、pending的环路 不计算价格等属性
        val trade =
          if (isP2PRes && (eventHeader.txStatus == TX_STATUS_PENDING || eventHeader.txStatus == TX_STATUS_FAILED)) {
            Activity.Trade(
              address = fill.owner,
              tokenBase = fill.tokenS,
              tokenQuote = fill.tokenB,
              isP2P = isP2PRes
            )
          } else {
            val market =
              metadataManager.getMarket(
                MarketPair(fill.tokenS, nextFill.tokenS)
              )
            val fillAmountS =
              metadataManager.getTokenWithAddress(fill.tokenS) match {
                case None        => BigInt(fill.amountS.get).doubleValue()
                case Some(token) => token.fromWei(fill.amountS)
              }
            val fillAmountB =
              metadataManager.getTokenWithAddress(nextFill.tokenS) match {
                case None        => BigInt(fill.amountB.get).doubleValue()
                case Some(token) => token.fromWei(fill.amountB)
              }
            val price = if (market.getMarketPair.baseToken == fill.tokenS) {
              fillAmountS / fillAmountB
            } else {
              fillAmountB / fillAmountS
            }
            Activity.Trade(
              address = fill.owner,
              tokenBase = market.baseTokenSymbol,
              tokenQuote = market.quoteTokenSymbol,
              amountBase = fill.amountS,
              amountQuote = fill.amountB,
              price = price.formatted(s"%.${market.priceDecimals}f"),
              isP2P = isP2PRes
            )
          }
        val activity = Activity(
          owner = fill.owner,
          block = eventHeader.getBlockHeader.height,
          txHash = eventHeader.txHash,
          txStatus = eventHeader.txStatus,
          activityType = ActivityType.TRADE_BUY, //TODO:
          timestamp = eventHeader.getBlockHeader.timestamp,
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
      tx: Transaction,
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
          miner = tx.from,
          blockHeight = blockHeader.height,
          blockTimestamp = blockHeader.timestamp
        )
    }
  }

  def extractInputToFill(tx: Transaction): Seq[Fill] = {
    ringSubmitterAbi.unpackFunctionInput(tx.input) match {
      case Some(params: SubmitRingsFunction.Params) =>
        new SimpleRingBatchDeserializer(Numeric.toHexString(params.data)).deserialize match {
          case Left(_) =>
            Seq.empty
          case Right(ringBatch) =>
            ringBatch.rings flatMap { ring =>
              ring.orderIndexes map { idx =>
                val order = ringBatch.orders(idx)
                val feeParams = order.getFeeParams
                Fill(
                  owner = order.owner,
                  orderHash = order.hash,
                  tokenS = order.tokenS,
                  tokenB = order.tokenB,
                  split = BigInt(feeParams.walletSplitPercentage),
                  fee = Some(
                    Fee(
                      tokenFee = feeParams.tokenFee,
                      walletSplitPercentage = feeParams.walletSplitPercentage
                    )
                  ),
                  miner = tx.from
                )
              }
            }
        }
      case _ =>
        Seq.empty
    }
  }

  def isP2P(
      fills: Seq[Fill],
      txFrom: String
    ): Boolean = {
    fills.exists { fill =>
      fill.owner == txFrom
    }
  }

}
