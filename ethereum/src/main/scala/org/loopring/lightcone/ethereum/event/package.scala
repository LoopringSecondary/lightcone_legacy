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

package org.loopring.lightcone.ethereum

import com.google.protobuf.ByteString
import org.loopring.lightcone.ethereum.abi.OrdersCancelledEvent.Result
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.MarketHashProvider.convert2Hex
import org.loopring.lightcone.proto.{OrdersCancelledEvent, _}
import org.web3j.utils.Numeric

import scala.collection.mutable.ListBuffer

package object event {
  val wethAbi = WETHABI()
  val ringSubmitterAbi = RingSubmitterAbi()
  val loopringProtocolAbi = LoopringProtocolAbi()

  def getBalanceAndAllowanceAdds(
      txs: Seq[(Transaction, Option[TransactionReceipt])],
      delegate: Address,
      protocol: Address
    ): (Seq[(String, String)], Seq[(String, String)]) = {
    val balanceAddresses = ListBuffer.empty[(String, String)]
    val allowanceAddresses = ListBuffer.empty[(String, String)]
    if (txs.forall(_._2.nonEmpty)) {
      txs.foreach(tx ⇒ {
        balanceAddresses.append(tx._2.get.from → Address.zeroAddress)
        if (Numeric.toBigInt(tx._2.get.status).intValue() == 1) {
          if (tx._1.input.isEmpty || tx._1.input.equals("0x") || tx._1.input
                .equals("0x0")) {
            balanceAddresses.append(tx._2.get.to → Address.zeroAddress)
          }
          wethAbi.unpackFunctionInput(tx._1.input) match {
            case Some(param: TransferFunction.Parms) ⇒
              balanceAddresses.append(
                tx._1.from → tx._1.to,
                param.to → tx._1.to
              )
            case Some(param: ApproveFunction.Parms) ⇒
              if (param.spender.equalsIgnoreCase(delegate.toString))
                allowanceAddresses.append(
                  tx._1.from → tx._1.to
                )
            case Some(param: TransferFromFunction.Parms) ⇒
              balanceAddresses.append(
                param.txFrom -> tx._1.to,
                param.to → tx._1.to
              )
            case _ ⇒
          }
        }
        tx._2.get.logs.foreach(log ⇒ {
          wethAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(transfer: TransferEvent.Result) ⇒
              balanceAddresses.append(
                transfer.from → log.address,
                transfer.receiver → log.address
              )
              if (tx._2.get.to.equalsIgnoreCase(protocol.toString)) {
                allowanceAddresses.append(transfer.from → log.address)
              }
            case Some(approval: ApprovalEvent.Result) ⇒
              if (approval.spender.equalsIgnoreCase(delegate.toString))
                allowanceAddresses.append(approval.owner → log.address)
            case Some(deposit: DepositEvent.Result) ⇒
              balanceAddresses.append(deposit.dst → log.address)
            case Some(withdrawal: WithdrawalEvent.Result) ⇒
              balanceAddresses.append(withdrawal.src → log.address)
            case _ ⇒
          }
        })
      })
    }
    (balanceAddresses.toSet.toSeq, allowanceAddresses.toSet.toSeq)
  }

  def getRingMinedEvent(
      receipts: Seq[Option[TransactionReceipt]]
    ): Seq[(RingMinedEvent.Result, TransactionReceipt)] = {
    if (receipts.forall(_.nonEmpty)) {
      receipts
        .flatMap(receipt ⇒ {
          receipt.get.logs.map { log ⇒
            {
              loopringProtocolAbi
                .unpackEvent(log.data, log.topics.toArray) match {
                case Some(event: RingMinedEvent.Result) ⇒
                  Some(event)
                case _ ⇒
                  None
              }
            }
          }.filter(_.nonEmpty).map(_.get → receipt.get)
        })
    } else {
      Seq.empty
    }
  }

  def getTrades(
      events: Seq[(RingMinedEvent.Result, TransactionReceipt)],
      blockTime: String
    ): Seq[Trade] = {
    events.flatMap(eventItem ⇒ {
      val (event, receipt) = eventItem
      val fills = splitEventToFills(event._fills)
      fills.zipWithIndex.map(item ⇒ {
        val (fill, index) = item
        val fill2 = if (index + 1 < fills.size) {
          fills(index + 1)
        } else {
          fills.head
        }
        val trade = Trade(
          orderHash = fill.substring(0, 2 + 64 * 1),
          owner = Address(fill.substring(2 + 64 * 1, 2 + 64 * 2)).toString,
          tokenS = Address(fill.substring(2 + 64 * 2, 2 + 64 * 3)).toString,
          tokenB = Address(fill2.substring(2 + 64 * 2, 2 + 64 * 3)).toString,
          amountS = Numeric
            .toBigInt(fill.substring(2 + 64 * 3, 2 + 64 * 4))
            .toByteArray,
          amountB = Numeric
            .toBigInt(fill2.substring(2 + 64 * 3, 2 + 64 * 4))
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
      })
    })
  }

  //TODO（yadong）等待孔亮提供解析的方法
  def getFailedRings(
      txs: Seq[(Transaction, Option[TransactionReceipt])]
    ): Seq[String] = {
    if (txs.forall(_._2.nonEmpty)) {
      txs
        .filter(tx ⇒ Numeric.toBigInt(tx._2.get.status).intValue() == 0)
        .filter(tx ⇒ {
          loopringProtocolAbi.unpackFunctionInput(tx._1.input) match {
            case Some(SubmitRingsFunction.Params) ⇒
              true
            case _ ⇒
              false
          }
        })
        .map(_._1.input)
    } else {
      Seq.empty
    }
  }

  def splitEventToFills(_fills: String): Seq[String] = {
    //首先去掉head 64 * 2
    val fillContent = Numeric.cleanHexPrefix(_fills).substring(128)
    val fillLength = 8 * 64
    (0 until (fillContent.length / fillLength)).map { index ⇒
      fillContent.substring(index * fillLength, fillLength * (index + 1))
    }
  }

  def getOrdersCancelledEvents(
      receipts: Seq[Option[TransactionReceipt]]
    ): Seq[OrdersCancelledEvent] = {

    if (receipts.forall(_.nonEmpty)) {
      receipts.flatMap(
        receipt ⇒
          receipt.get.logs.flatMap { log ⇒
            {
              loopringProtocolAbi
                .unpackEvent(log.data, log.topics.toArray) match {
                case Some(event: Result) ⇒
                  event._orderHashes
                    .map(orderHash ⇒ {
                      OrdersCancelledEvent(
                        orderHash = orderHash,
                        blockHeight =
                          Numeric.toBigInt(receipt.get.blockNumber).longValue(),
                        brokerOrOwner = event.address,
                        txHash = receipt.get.transactionHash
                      )
                    })
                case _ ⇒
                  Seq.empty[OrdersCancelledEvent]
              }
            }
          }
      )
    } else {
      Seq.empty[OrdersCancelledEvent]
    }
  }

  def getOrdersCutoffEvent(
      receipts: Seq[Option[TransactionReceipt]]
    ): Seq[OrdersCutoffEvent] = {
    receipts.flatMap { receipt ⇒
      receipt.get.logs.map { log ⇒
        loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
          case Some(event: AllOrdersCancelledEvent.Result) ⇒
            Some(
              OrdersCutoffEvent(
                txHash = receipt.get.transactionHash,
                blockHeight =
                  Numeric.toBigInt(receipt.get.blockNumber).longValue(),
                broker = event._broker,
                cutoff = event._cutoff.longValue()
              )
            )
          case Some(event: AllOrdersCancelledByBrokerEvent.Result) ⇒
            Some(
              OrdersCutoffEvent(
                txHash = receipt.get.transactionHash,
                blockHeight =
                  Numeric.toBigInt(receipt.get.blockNumber).longValue(),
                broker = event._broker,
                owner = event._owner,
                cutoff = event._cutoff.longValue()
              )
            )
          case Some(event: AllOrdersCancelledForTradingPairEvent.Result) ⇒
            Some(
              OrdersCutoffEvent(
                txHash = receipt.get.transactionHash,
                blockHeight =
                  Numeric.toBigInt(receipt.get.blockNumber).longValue(),
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
                txHash = receipt.get.transactionHash,
                blockHeight =
                  Numeric.toBigInt(receipt.get.blockNumber).longValue(),
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

  def getOnlineOrders(
      receipts: Seq[Option[TransactionReceipt]]
    ): Seq[RawOrder] = {
    if (receipts.forall(_.nonEmpty)) {
      receipts.flatMap(receipt ⇒ {
        receipt.get.logs.map { log ⇒
          loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(event: OrderSubmittedEvent.Result) ⇒
              Some(extractOrderFromEvent(event))
            case _ ⇒
              None
          }
        }.filter(_.nonEmpty).flatten
      })
    } else {
      Seq.empty[RawOrder]
    }
  }

  def extractOrderFromEvent(event: OrderSubmittedEvent.Result): RawOrder = {

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

  def getTokenTierUpgradedEvent(
      receipts: Seq[Option[TransactionReceipt]]
    ): Seq[TokenTierUpgradedEvent.Result] = {
    if (receipts.forall(_.nonEmpty)) {
      receipts.flatMap(receipt ⇒ {
        receipt.get.logs.map { log ⇒
          loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(event: TokenTierUpgradedEvent.Result) ⇒
              Some(event)
            case _ ⇒
              None
          }
        }.filter(_.nonEmpty).flatten
      })
    } else {
      Seq.empty
    }
  }

  implicit def bytes2ByteString(bytes: Array[Byte]): ByteString =
    ByteString.copyFrom(bytes)

}
