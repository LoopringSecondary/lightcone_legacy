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
import io.lightcone.relayer.data._
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
      extractInputToRingBatch(txdata.tx.input) match {
        case None => Seq.empty
        case Some(ringBatch) =>
          val isP2PRes = isP2P(ringBatch.orders, txdata.tx.from)
          txdata.receiptAndHeaderOpt match {
            case Some((receipt, eventHeader)) =>
              //失败的交易只有在p2p时才生成Activity，否则只生成RingMinedEvent
              if (receipt.status == TX_STATUS_FAILED) {
                val ringMinedEvents =
                  ringBatchToRingMinedEvents(ringBatch, eventHeader)
                val activities =
                  if (isP2PRes)
                    ringBatchToActivities(
                      ringBatch,
                      txdata.tx.hash,
                      TX_STATUS_SUCCESS,
                      isP2PRes
                    )
                  else Seq.empty
                ringMinedEvents ++ activities
              } else {
                val orders = ringBatch.orders map { order =>
                  order.hash -> order
                }

                extractSuccessedEvents(
                  txdata.tx,
                  receipt,
                  eventHeader,
                  orders.toMap,
                  ringBatch.feeRecipient,
                  isP2PRes
                )
              }
            case None =>
              //pending的只生成activity
              if (isP2PRes) {
                ringBatchToActivities(
                  ringBatch,
                  txdata.tx.hash,
                  TX_STATUS_PENDING,
                  isP2PRes
                )
              } else {
                Seq.empty
              }
          }
      }
    }
  }

  private def extractSuccessedEvents(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: EventHeader,
      orders: Map[String, RawOrder],
      feeReceipt: String,
      isP2PRes: Boolean
    ) = {
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
              orders,
              feeReceipt,
              event,
              tx,
              eventHeader.getBlockHeader
            )
            val ringMinedEvents =
              generateRingMinedEvents(fills, eventHeader)
            val ohlcDatas = generateOHLCDatas(
              fills,
              event._ringIndex.longValue(),
              eventHeader
            )
            val orderFilledEvents = fills.map(
              fill =>
                OrderFilledEvent(
                  header = Some(eventHeader),
                  owner = fill.owner,
                  orderHash = fill.orderHash
                )
            )
            val activities = genereateActivity(fills, eventHeader, isP2PRes)
            fills ++ ringMinedEvents ++ ohlcDatas ++ activities ++ orderFilledEvents
          case _ =>
            Seq.empty
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
            metadataManager.getMarket(marketHash).getMetadata
          val marketPair = marketMetadata.getMarketPair
          val baseToken =
            metadataManager.getTokenWithAddress(marketPair.baseToken).get
          val quoteToken =
            metadataManager.getTokenWithAddress(marketPair.quoteToken).get
          val (baseAmount, quoteAmount) =
            getAmounts(fill, baseToken, quoteToken, marketMetadata)
          Some(
            OHLCRawData(
              blockHeight = eventHeader.getBlockHeader.height,
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
      eventHeader: EventHeader,
      isP2PRes: Boolean
    ): Seq[Activity] = {
    val activities = fills.zipWithIndex.map {
      case (fill, index) =>
        val nextFill =
          if (index + 1 >= fills.size) fills.head else fills(index + 1)

        val marketOpt =
          metadataManager
            .getMarket(
              MarketPair(fill.tokenS, nextFill.tokenS)
            )
            .metadata
        val (baseTokenSymbol, quoteTokenSymbol, price) = marketOpt match {
          case None => ("", "", "0.0")
          case Some(market) =>
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
            val p = (if (market.getMarketPair.baseToken == fill.tokenS) {
                       fillAmountS / fillAmountB
                     } else {
                       fillAmountB / fillAmountS
                     }).formatted(s"%.${market.priceDecimals}f")
            (market.baseTokenSymbol, market.quoteTokenSymbol, p)
        }

        val trade = Activity.Trade(
          address = fill.owner,
          tokenBase = baseTokenSymbol,
          tokenQuote = quoteTokenSymbol,
          amountBase = fill.amountS,
          amountQuote = fill.amountB,
          price = price,
          isP2P = isP2PRes
        )

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
      orders: Map[String, RawOrder],
      feeRecipeit: String,
      eventRes: RingMinedEvent.Result,
      tx: Transaction,
      blockHeader: BlockHeader
    ): Seq[Fill] = {
    fillData.zipWithIndex map {
      case (fill, index) =>
        val nextFill =
          if (index + 1 >= fillData.size) fillData.head else fillData(index + 1)
        val data = Numeric.cleanHexPrefix(fill)
        val orderHash = Numeric.prependHexPrefix(data.substring(0, 64 * 1))
        val order = orders.getOrElse(orderHash, RawOrder())
        val nextFillData = Numeric.cleanHexPrefix(nextFill)
        val nextOrder = orders.getOrElse(
          Numeric.prependHexPrefix(nextFillData.substring(0, 64 * 1)),
          RawOrder()
        )
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
          fee = Some(
            Fee(
              tokenFee = order.getFeeParams.tokenFee,
              amountFee =
                NumericConversion.toBigInt(data.substring(64 * 5, 64 * 6)),
              feeAmountS =
                NumericConversion.toBigInt(data.substring(64 * 6, 64 * 7)),
              feeAmountB =
                NumericConversion.toBigInt(data.substring(64 * 7, 64 * 8)),
              feeRecipient = feeRecipeit,
              waiveFeePercentage = order.getFeeParams.waiveFeePercentage,
              walletSplitPercentage = order.getFeeParams.walletSplitPercentage
            )
          ),
          wallet = order.getParams.wallet,
          miner = tx.from,
          blockHeight = blockHeader.height,
          blockTimestamp = blockHeader.timestamp,
          isTaker = order.validSince >= nextOrder.validSince
        )
    }
  }

  private def ringBatchToRingMinedEvents(
      ringBatch: RingBatch,
      eventHeader: EventHeader
    ): Seq[PRingMinedEvent] = {
    ringBatch.rings map { ring =>
      val order1 = ringBatch.orders(ring.orderIndexes(0))
      val order2 = ringBatch.orders(ring.orderIndexes(1))
      PRingMinedEvent(
        header = Some(eventHeader),
        orderIds = Seq(order1.hash, order2.hash),
        marketPair = Some(MarketPair(order1.tokenS, order2.tokenS))
      )
    }
  }

  private def ringBatchToActivities(
      ringBatch: RingBatch,
      txHash: String,
      txStatus: TxStatus,
      isP2PRes: Boolean
    ): Seq[Activity] = {
    ringBatch.rings flatMap { ring =>
      ringBatch.rings flatMap { ring =>
        ring.orderIndexes flatMap { idx =>
          val order = ringBatch.orders(idx)
          val feeParams = order.getFeeParams
          val trade = Activity.Trade(
            address = order.owner,
            tokenBase = order.tokenS,
            tokenQuote = order.tokenB,
            isP2P = isP2PRes
          )
          val activity = Activity(
            owner = order.owner,
            txHash = txHash,
            txStatus = txStatus,
            activityType = ActivityType.TRADE_BUY,
            token = order.tokenS,
            detail = Activity.Detail.Trade(trade)
          )
          Seq(
            activity,
            activity
              .copy(
                activityType = ActivityType.TRADE_SELL,
                token = order.tokenB
              )
          )
        }
      }
    }
  }

  private def extractInputToRingBatch(inputData: String): Option[RingBatch] = {
    ringSubmitterAbi.unpackFunctionInput(inputData) match {
      case Some(params: SubmitRingsFunction.Params) =>
        new SimpleRingBatchDeserializer(Numeric.toHexString(params.data)).deserialize match {
          case Left(_) =>
            None
          case Right(ringBatch) =>
            Some(ringBatch)
        }
      case _ =>
        None
    }
  }

  private def isP2p(order: RawOrder) = {
    order.getFeeParams.tokenBFeePercentage != 0 ||
    order.getFeeParams.tokenSFeePercentage != 0
  }

  //目前只允许p2p与p2p订单撮合
  private def isP2P(
      orders: Seq[RawOrder],
      txFrom: String
    ): Boolean = {
    //判断订单是否为p2p订单
    orders.exists(
      order =>
        order.getFeeParams.tokenBFeePercentage != 0 ||
          order.getFeeParams.tokenSFeePercentage != 0
    )
//    orders.exists { order =>
//      order.owner == txFrom
//    }
  }

}
