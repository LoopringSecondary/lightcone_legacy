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

package org.loopring.lightcone.ethereum.event

import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.MarketHashProvider.convert2Hex
import org.loopring.lightcone.proto.{
  OrdersCancelledEvent ⇒ POrdersCancelledEvent,
  RingMinedEvent ⇒ PRingMinedEvent,
  _
}
import org.web3j.utils.Numeric

trait DataExtractor[R] extends Extractor {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[R]

  def getEventHeader(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): EventHeader = {
    EventHeader(
      txHash = tx.hash,
      txFrom = tx.from,
      txTo = tx.to,
      txValue = Numeric.toBigInt(tx.value).toByteArray,
      txIndex = Numeric.toBigInt(tx.transactionIndex).intValue(),
      txStatus = getStatus(receipt.status),
      blockHash = tx.blockHash,
      blockTimestamp = Numeric.toBigInt(blockTime).longValue(),
      blockNumber = Numeric.toBigInt(tx.blockNumber).longValue()
    )
  }
}

class RingMinedEventExtractor() extends DataExtractor[PRingMinedEvent] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[PRingMinedEvent] = {
    val header = getEventHeader(tx, receipt, blockTime)
    receipt.logs.zipWithIndex.map { item ⇒
      {
        val (log, index) = item
        loopringProtocolAbi
          .unpackEvent(log.data, log.topics.toArray) match {
          case Some(event: RingMinedEvent.Result) ⇒
            val fillContent =
              Numeric.cleanHexPrefix(event._fills).substring(128)
            val fillLength = 8 * 64
            val orderFilledEvents =
              (0 until (fillContent.length / fillLength)).map { index ⇒
                fillContent.substring(
                  index * fillLength,
                  fillLength * (index + 1)
                )
              }.map { fill ⇒
                fillToOrderFilledEvent(
                  fill,
                  event,
                  receipt,
                  Some(header.withLogIndex(index))
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
          case _ ⇒
            None
        }
      }
    }.filter(_.nonEmpty).map(_.get)
  }

  private def fillToOrderFilledEvent(
      fill: String,
      event: RingMinedEvent.Result,
      receipt: TransactionReceipt,
      header: Option[EventHeader]
    ): OrderFilledEvent = {
    OrderFilledEvent(
      header = header,
      owner = Address(fill.substring(2 + 64 * 1, 2 + 64 * 2)).toString,
      orderHash = fill.substring(0, 2 + 64 * 1),
      ringHash = event._ringHash,
      ringIndex = event._ringIndex.longValue(),
      filledAmountS =
        Numeric.toBigInt(fill.substring(2 + 64 * 3, 2 + 64 * 4)).toByteArray,
      filledAmountFee = Numeric
        .toBigInt(fill.substring(2 + 64 * 5, 2 + 64 * 6))
        .toByteArray,
      feeAmountS = Numeric
        .toBigInt(fill.substring(2 + 64 * 6, 2 + 64 * 7))
        .toByteArray,
      feeAmountB = Numeric
        .toBigInt(fill.substring(2 + 64 * 7, 2 + 64 * 8))
        .toByteArray,
      feeRecipient = event._feeRecipient
    )
  }

}

class OrdersCancelledEventExtractor()
    extends DataExtractor[POrdersCancelledEvent] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[POrdersCancelledEvent] = {
    receipt.logs.map { log ⇒
      {
        loopringProtocolAbi
          .unpackEvent(log.data, log.topics.toArray) match {
          case Some(event: OrdersCancelledEvent.Result) ⇒
            Some(
              POrdersCancelledEvent(
                header = Some(getEventHeader(tx, receipt, blockTime)),
                broker = event.address,
                orderHashes = event._orderHashes
              )
            )
          case _ ⇒
            None
        }
      }
    }.filter(_.nonEmpty).map(_.get)
  }
}

class CutOffEventExtractor() extends DataExtractor[CutoffEvent] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[CutoffEvent] = {
    receipt.logs.map { log ⇒
      loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
        case Some(event: AllOrdersCancelledEvent.Result) ⇒
          Some(
            CutoffEvent(
              header = Some(getEventHeader(tx, receipt, blockTime)),
              cutoff = event._cutoff.longValue(),
              broker = event._broker
            )
          )
        case _ ⇒
          None
      }
    }.filter(_.nonEmpty).map(_.get)
  }
}

class OwnerCutoffEventExtractor() extends DataExtractor[OwnerCutoffEvent] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[OwnerCutoffEvent] = {
    receipt.logs.map { log ⇒
      loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
        case Some(event: AllOrdersCancelledByBrokerEvent.Result) ⇒
          Some(
            OwnerCutoffEvent(
              header = Some(getEventHeader(tx, receipt, blockTime)),
              cutoff = event._cutoff.longValue(),
              broker = event._broker,
              owner = event._owner
            )
          )
        case _ ⇒
          None
      }
    }.filter(_.nonEmpty).map(_.get)
  }
}

class TradingPairCutoffEventExtractor()
    extends DataExtractor[TradingPairCutoffEvent] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[TradingPairCutoffEvent] = {
    receipt.logs.map { log ⇒
      loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
        case Some(event: AllOrdersCancelledForTradingPairEvent.Result) ⇒
          Some(
            TradingPairCutoffEvent(
              header = Some(getEventHeader(tx, receipt, blockTime)),
              cutoff = event._cutoff.longValue(),
              broker = event._broker,
              tradingPair = convert2Hex(event._token1, event._token2)
            )
          )
        case _ ⇒
          None
      }
    }.filter(_.nonEmpty).map(_.get)
  }
}

class OwnerTradingPairCutoffEventExtractor()
    extends DataExtractor[OwnerTradingPairCutoffEvent] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[OwnerTradingPairCutoffEvent] = {
    receipt.logs.map { log ⇒
      loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
        case Some(event: AllOrdersCancelledForTradingPairByBrokerEvent.Result) ⇒
          Some(
            OwnerTradingPairCutoffEvent(
              header = Some(getEventHeader(tx, receipt, blockTime)),
              cutoff = event._cutoff.longValue(),
              broker = event._broker,
              owner = event._owner,
              tradingPair = convert2Hex(event._token1, event._token2)
            )
          )
        case _ ⇒
          None
      }
    }.filter(_.nonEmpty).map(_.get)
  }
}

class OnlineOrderExtractor() extends DataExtractor[RawOrder] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[RawOrder] = {
    receipt.logs.map { log ⇒
      loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
        case Some(event: OrderSubmittedEvent.Result) ⇒
          Some(extractOrderFromEvent(event))
        case _ ⇒
          None
      }
    }.filter(_.nonEmpty).map(_.get)
  }

  private def extractOrderFromEvent(
      event: OrderSubmittedEvent.Result
    ): RawOrder = {
    // 去掉head 2 * 64
    val data = Numeric.cleanHexPrefix(event.orderData).substring(128)
    RawOrder(
      owner = Numeric.prependHexPrefix(data.substring(0, 64)),
      tokenS = Numeric.prependHexPrefix(data.substring(64, 64 * 2)),
      tokenB = Numeric.prependHexPrefix(data.substring(64 * 2, 64 * 3)),
      amountS = Numeric.toBigInt(data.substring(64 * 3, 64 * 4)).toByteArray,
      amountB = Numeric.toBigInt(data.substring(64 * 4, 64 * 5)).toByteArray,
      validSince = Numeric.toBigInt(data.substring(64 * 5, 64 * 6)).intValue(),
      params = Some(
        RawOrder.Params(
          broker = Numeric.prependHexPrefix(data.substring(64 * 6, 64 * 7)),
          orderInterceptor =
            Numeric.prependHexPrefix(data.substring(64 * 7, 64 * 8)),
          wallet = Numeric.prependHexPrefix(data.substring(64 * 8, 64 * 9)),
          validUntil =
            Numeric.toBigInt(data.substring(64 * 9, 64 * 10)).intValue(),
          allOrNone = Numeric
            .toBigInt(data.substring(64 * 10, 64 * 11))
            .intValue() == 1,
          tokenStandardS = TokenStandard.fromValue(
            Numeric.toBigInt(data.substring(64 * 17, 64 * 18)).intValue()
          ),
          tokenStandardB = TokenStandard.fromValue(
            Numeric.toBigInt(data.substring(64 * 18, 64 * 19)).intValue()
          ),
          tokenStandardFee = TokenStandard.fromValue(
            Numeric.toBigInt(data.substring(64 * 19, 64 * 20)).intValue()
          )
        )
      ),
      hash = event.orderHash,
      feeParams = Some(
        RawOrder.FeeParams(
          tokenFee = Numeric.prependHexPrefix(data.substring(64 * 11, 64 * 12)),
          amountFee =
            Numeric.toBigInt(data.substring(64 * 12, 64 * 13)).toByteArray,
          tokenBFeePercentage =
            Numeric.toBigInt(data.substring(64 * 13, 64 * 14)).intValue(),
          tokenSFeePercentage =
            Numeric.toBigInt(data.substring(64 * 14, 64 * 15)).intValue(),
          tokenRecipient =
            Numeric.prependHexPrefix(data.substring(64 * 15, 64 * 16)),
          walletSplitPercentage =
            Numeric.toBigInt(data.substring(64 * 16, 64 * 17)).intValue()
        )
      ),
      erc1400Params = Some(
        RawOrder.ERC1400Params(
          trancheS = Numeric.prependHexPrefix(data.substring(64 * 20, 64 * 21)),
          trancheB = Numeric.prependHexPrefix(data.substring(64 * 21, 64 * 22)),
          transferDataS =
            Numeric.prependHexPrefix(data.substring(64 * 22, 64 * 23))
        )
      )
    )
  }
}

class TokenBurnRateEventExtractor(
    rateMap: Map[String, Int],
    base: Int)
    extends DataExtractor[TokenBurnRateEvent] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[TokenBurnRateEvent] = {
    receipt.logs.zipWithIndex.map { log ⇒
      loopringProtocolAbi.unpackEvent(log._1.data, log._1.topics.toArray) match {
        case Some(event: TokenTierUpgradedEvent.Result) ⇒
          Some(
            TokenBurnRateEvent(
              header = Some(
                getEventHeader(tx, receipt, blockTime)
                  .withLogIndex(log._2)
              ),
              token = event.add,
              burnRate = rateMap(Address(event.add).toString) / base.toDouble
            )
          )
        case _ ⇒
          None
      }
    }.filter(_.nonEmpty).map(_.get)
  }
}
