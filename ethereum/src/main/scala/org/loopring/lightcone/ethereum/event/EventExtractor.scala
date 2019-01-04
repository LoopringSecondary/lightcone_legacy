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

import org.loopring.lightcone.ethereum.abi.OrdersCancelledEvent.Result
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.MarketHashProvider.convert2Hex
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.OrdersCancelledEvent
import org.web3j.utils.Numeric

trait EventExtractor[R] {
  def extract(txs: Seq[(Transaction, TransactionReceipt)]): Seq[R]
}

case class TradeExtractor()(implicit blockTime: String)
    extends EventExtractor[Trade] {

  override def extract(
      txs: Seq[(Transaction, TransactionReceipt)]
    ): Seq[Trade] = {
    txs
      .flatMap(item ⇒ {
        val (_, receipt) = item
        receipt.logs.map { log ⇒
          {
            loopringProtocolAbi
              .unpackEvent(log.data, log.topics.toArray) match {
              case Some(event: RingMinedEvent.Result) ⇒
                Some(event)
              case _ ⇒
                None
            }
          }
        }.filter(_.nonEmpty).flatMap { event ⇒
          extractTrades(event.get, receipt)
        }
      })
  }

  private def extractTrades(
      event: RingMinedEvent.Result,
      receipt: TransactionReceipt
    )(
      implicit blockTime: String
    ): Seq[Trade] = {
    //首先去掉head 64 * 2
    val fillContent = Numeric.cleanHexPrefix(event._fills).substring(128)
    val fillLength = 8 * 64
    val fills = (0 until (fillContent.length / fillLength)).map { index ⇒
      fillContent.substring(index * fillLength, fillLength * (index + 1))
    }
    fills.zipWithIndex.map(item ⇒ {
      val (fill, index) = item
      val fill2 = if (index + 1 < fills.size) {
        fills(index + 1)
      } else {
        fills.head
      }
      fillToTrade(fill, fill2, event, receipt)
    })
  }

  private def fillToTrade(
      fill: String,
      _fill: String,
      event: RingMinedEvent.Result,
      receipt: TransactionReceipt
    )(
      implicit blockTime: String
    ): Trade = {

    val trade = Trade(
      orderHash = fill.substring(0, 2 + 64 * 1),
      owner = Address(fill.substring(2 + 64 * 1, 2 + 64 * 2)).toString,
      tokenS = Address(fill.substring(2 + 64 * 2, 2 + 64 * 3)).toString,
      tokenB = Address(_fill.substring(2 + 64 * 2, 2 + 64 * 3)).toString,
      amountS = Numeric
        .toBigInt(fill.substring(2 + 64 * 3, 2 + 64 * 4))
        .toByteArray,
      amountB = Numeric
        .toBigInt(_fill.substring(2 + 64 * 3, 2 + 64 * 4))
        .toByteArray,
      split = Numeric
        .toBigInt(fill.substring(2 + 64 * 4, 2 + 64 * 5))
        .toByteArray,
      fees = Some(
        Trade.Fees(
          amountFee = Numeric
            .toBigInt(fill.substring(2 + 64 * 5, 2 + 64 * 6))
            .toByteArray,
          feeAmountS = Numeric
            .toBigInt(fill.substring(2 + 64 * 6, 2 + 64 * 7))
            .toByteArray,
          feeAmountB = Numeric
            .toBigInt(fill.substring(2 + 64 * 7, 2 + 64 * 8))
            .toByteArray
        )
      ),
      txHash = receipt.transactionHash,
      blockHeight = Numeric.toBigInt(receipt.blockNumber).longValue(),
      blockTimestamp = Numeric.toBigInt(blockTime).longValue(),
      ringHash = event._ringHash,
      ringIndex = event._ringIndex.longValue(),
      //TODO(yadong) 尝试在事件中找到该地址
      delegateAddress = ""
    )
    trade.withMarketHash(convert2Hex(trade.tokenB, trade.tokenS))
  }

}

case class OrdersCancelledExtractor()
    extends EventExtractor[OrdersCancelledEvent] {
  override def extract(
      txs: Seq[(Transaction, TransactionReceipt)]
    ): Seq[OrdersCancelledEvent] = {
    txs.unzip._2.flatMap(
      receipt ⇒
        receipt.logs.flatMap { log ⇒
          {
            loopringProtocolAbi
              .unpackEvent(log.data, log.topics.toArray) match {
              case Some(event: Result) ⇒
                event._orderHashes
                  .map(orderHash ⇒ {
                    OrdersCancelledEvent(
                      orderHash = orderHash,
                      blockHeight =
                        Numeric.toBigInt(receipt.blockNumber).longValue(),
                      brokerOrOwner = event.address,
                      txHash = receipt.transactionHash
                    )
                  })
              case _ ⇒
                Seq.empty[OrdersCancelledEvent]
            }
          }
        }
    )
  }
}

case class CutOffExtractor() extends EventExtractor[OrdersCutoffEvent] {
  override def extract(
      txs: Seq[(Transaction, TransactionReceipt)]
    ): Seq[OrdersCutoffEvent] = {
    txs.unzip._2.flatMap { receipt ⇒
      receipt.logs.map { log ⇒
        loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
          case Some(event: AllOrdersCancelledEvent.Result) ⇒
            Some(
              OrdersCutoffEvent(
                txHash = receipt.transactionHash,
                blockHeight = Numeric.toBigInt(receipt.blockNumber).longValue(),
                broker = event._broker,
                cutoff = event._cutoff.longValue()
              )
            )
          case Some(event: AllOrdersCancelledByBrokerEvent.Result) ⇒
            Some(
              OrdersCutoffEvent(
                txHash = receipt.transactionHash,
                blockHeight = Numeric.toBigInt(receipt.blockNumber).longValue(),
                broker = event._broker,
                owner = event._owner,
                cutoff = event._cutoff.longValue()
              )
            )
          case Some(event: AllOrdersCancelledForTradingPairEvent.Result) ⇒
            Some(
              OrdersCutoffEvent(
                txHash = receipt.transactionHash,
                blockHeight = Numeric.toBigInt(receipt.blockNumber).longValue(),
                broker = event._broker,
                cutoff = event._cutoff.longValue(),
                tradingPair = convert2Hex(
                  Address(event._token1).toString,
                  Address(event._token2).toString
                )
              )
            )
          case Some(
              event: AllOrdersCancelledForTradingPairByBrokerEvent.Result
              ) ⇒
            Some(
              OrdersCutoffEvent(
                txHash = receipt.transactionHash,
                blockHeight = Numeric.toBigInt(receipt.blockNumber).longValue(),
                broker = event._broker,
                owner = event._owner,
                cutoff = event._cutoff.longValue(),
                tradingPair = convert2Hex(
                  Address(event._token1).toString,
                  Address(event._token2).toString
                )
              )
            )
          case _ ⇒
            None
        }
      }.filter(_.nonEmpty).map(_.get)
    }
  }
}

case class OrderEvent() extends EventExtractor[RawOrder] {
  override def extract(
      txs: Seq[(Transaction, TransactionReceipt)]
    ): Seq[RawOrder] = {
    txs.unzip._2.flatMap(receipt ⇒ {
      receipt.logs.map { log ⇒
        loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
          case Some(event: OrderSubmittedEvent.Result) ⇒
            Some(extractOrderFromEvent(event))
          case _ ⇒
            None
        }
      }.filter(_.nonEmpty).map(_.get)
    })
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

case class FailedRingsExtractor() extends EventExtractor[String] {
  //TODO（yadong）等待孔亮提供解析的方法
  override def extract(
      txs: Seq[(Transaction, TransactionReceipt)]
    ): Seq[String] = {
    txs
      .filter(tx ⇒ Numeric.toBigInt(tx._2.status).intValue() == 0)
      .filter(tx ⇒ {
        loopringProtocolAbi.unpackFunctionInput(tx._1.input) match {
          case Some(SubmitRingsFunction.Params) ⇒
            true
          case _ ⇒
            false
        }
      })
      .map(_._1.input)
  }
}

case class TokenTierUpgradedExtractor()
    extends EventExtractor[TokenTierUpgradedEvent.Result] {
  override def extract(
      txs: Seq[(Transaction, TransactionReceipt)]
    ): Seq[TokenTierUpgradedEvent.Result] = {
    txs.unzip._2.flatMap(receipt ⇒ {
      receipt.logs.map { log ⇒
        loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
          case Some(event: TokenTierUpgradedEvent.Result) ⇒
            Some(event)
          case _ ⇒
            None
        }
      }.filter(_.nonEmpty).map(_.get)
    })
  }
}
