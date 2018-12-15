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

package org.loopring.lightcone.actors

import com.google.protobuf.ByteString
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.MarketHashProvider.convert2Hex
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.data._

import org.web3j.utils.Numeric

import scala.collection.mutable.ListBuffer

package object ethereum {

  type ProtoBuf[T] = scalapb.GeneratedMessage with scalapb.Message[T]

  private[ethereum] case class JsonRpcReqWrapped(
      id: Int,
      jsonrpc: String = "2.0",
      method: String,
      params: Any) {
    private implicit val formats = Serialization.formats(NoTypeHints)
    def toProto: XJsonRpcReq = XJsonRpcReq(write(this))
  }

  private[ethereum] case class JsonRpcResWrapped(
      id: Any,
      jsonrpc: String = "2.0",
      result: Any,
      error: Option[XJsonRpcErr]
    )

  private[ethereum] object JsonRpcResWrapped {
    private implicit val formats = DefaultFormats

    def toJsonRpcResWrapped: PartialFunction[XJsonRpcRes, JsonRpcResWrapped] = {
      case j: XJsonRpcRes => parse(j.json).extract[JsonRpcResWrapped]
    }
  }

  private[ethereum] case class BatchMethod(
      id: Int,
      method: String,
      params: Seq[Any])

  val erc20Abi = ERC20ABI()
  val wethAbi = WETHABI()
  val tradeHistoryAbi = TradeHistoryAbi()

  val zeroAdd: String = "0x" + "0" * 40
  val loopringProtocolAbi = LoopringProtocolAbi()

  implicit def xGetBalanceAndAllowanceToBatchReq(
      delegateAddress: Address,
      req: XGetBalanceAndAllowancesReq
    ): XBatchContractCallReq = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val allowanceCallReqs =
      batchErc20AllowanceReq(delegateAddress, owner, tokens)
    val balanceCallReqs = batchErc20BalanceReq(owner, tokens)
    XBatchContractCallReq(allowanceCallReqs ++ balanceCallReqs)
  }

  implicit def xGetBalanceToBatchReq(
      req: XGetBalanceReq
    ): XBatchContractCallReq = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val balanceCallReqs = batchErc20BalanceReq(owner, tokens)
    XBatchContractCallReq(balanceCallReqs)
  }

  implicit def xGetAllowanceToBatchReq(
      delegateAddress: Address,
      req: XGetAllowanceReq
    ): XBatchContractCallReq = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val allowanceCallReqs =
      batchErc20AllowanceReq(delegateAddress, owner, tokens)
    XBatchContractCallReq(allowanceCallReqs)
  }

  implicit def xGetFilledAmountToBatchReq(
      tradeHistoryAddress: Address,
      req: GetFilledAmountReq
    ): XBatchContractCallReq = {
    val batchFilledAmountReqs =
      batchFilledAmountReq(tradeHistoryAddress, req.orderIds)
    XBatchContractCallReq(batchFilledAmountReqs)
  }

  implicit def xBatchContractCallResToBalanceAndAllowance(
      address: String,
      tokens: Seq[String],
      batchRes: XBatchContractCallRes
    ): XGetBalanceAndAllowancesRes = {

    val allowances = batchRes.resps.filter(_.id % 2 == 0).map { res =>
      ByteString.copyFrom(Numeric.hexStringToByteArray(res.result))
    }
    val balances = batchRes.resps.filter(_.id % 2 == 1).map { res =>
      ByteString.copyFrom(Numeric.hexStringToByteArray(res.result))
    }
    val balanceAndAllowance = (balances zip allowances).map { ba =>
      XBalanceAndAllowance(ba._1, ba._2)
    }
    XGetBalanceAndAllowancesRes(address, (tokens zip balanceAndAllowance).toMap)
  }

  implicit def xBatchContractCallResToBalance(
      address: String,
      tokens: Seq[String],
      batchRes: XBatchContractCallRes
    ): XGetBalanceRes = {
    val balances = batchRes.resps.filter(_.id % 2 == 1).map { res =>
      ByteString.copyFrom(Numeric.hexStringToByteArray(res.result))
    }
    XGetBalanceRes(address, (tokens zip balances).toMap)
  }

  implicit def xBatchContractCallResToAllowance(
      address: String,
      tokens: Seq[String],
      batchRes: XBatchContractCallRes
    ): XGetAllowanceRes = {
    val allowances = batchRes.resps.filter(_.id % 2 == 0).map { res =>
      ByteString.copyFrom(Numeric.hexStringToByteArray(res.result))
    }
    XGetAllowanceRes(address, (tokens zip allowances).toMap)
  }

  private def batchFilledAmountReq(
      contractAddress: Address,
      orderHashes: Seq[String],
      tag: String = "latest"
    ) = {
    orderHashes.zipWithIndex.map { orderHash ⇒
      val data = tradeHistoryAbi.filled.pack(
        FilledFunction.Params(Numeric.hexStringToByteArray(orderHash._1))
      )
      val param = XTransactionParam(to = contractAddress.toString, data = data)
      XEthCallReq(orderHash._2, Some(param), tag)
    }
  }

  private def batchErc20AllowanceReq(
      delegateAddress: Address,
      owner: Address,
      tokens: Seq[Address],
      tag: String = "latest"
    ) = {
    tokens.zipWithIndex.map(token => {
      val data = erc20Abi.allowance.pack(
        AllowanceFunction
          .Parms(_spender = delegateAddress.toString, _owner = owner.toString)
      )
      val param = XTransactionParam(to = token._1.toString, data = data)
      XEthCallReq(token._2 * 2, Some(param), tag)
    })
  }

  private def batchErc20BalanceReq(
      owner: Address,
      tokens: Seq[Address],
      tag: String = "latest"
    ) = {
    tokens.zipWithIndex.map { token =>
      val data = erc20Abi.balanceOf.pack(
        BalanceOfFunction.Parms(_owner = owner.toString)
      )
      val param = XTransactionParam(to = token._1.toString, data = data)
      XEthCallReq(1 + token._2 * 2, Some(param), tag)
    }
  }

  def getBalanceAndAllowanceAdds(
      receipts: Seq[Option[XTransactionReceipt]],
      delegate: Address,
      protocol: Address
    ): (Seq[(String, String)], Seq[(String, String)]) = {
    val balanceAddresses = ListBuffer.empty[(String, String)]
    val allowanceAddresses = ListBuffer.empty[(String, String)]
    if (receipts.forall(_.nonEmpty)) {
      receipts.foreach(receipt ⇒ {
        balanceAddresses.append(receipt.get.from → zeroAdd)
        balanceAddresses.append(receipt.get.to → zeroAdd)
        receipt.get.logs.foreach(log ⇒ {
          wethAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(transfer: TransferEvent.Result) ⇒
              balanceAddresses.append(
                transfer.sender → log.address,
                transfer.receiver → log.address
              )
              if (receipt.get.to.equalsIgnoreCase(protocol.toString)) {
                allowanceAddresses.append(transfer.sender → log.address)
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
    (balanceAddresses, allowanceAddresses)
  }

  def getFills(receipts: Seq[Option[XTransactionReceipt]]): Seq[String] = {

    if (receipts.forall(_.nonEmpty)) {
      receipts
        .flatMap(receipt ⇒ {
          receipt.get.logs.flatMap { log ⇒
            {
              loopringProtocolAbi
                .unpackEvent(log.data, log.topics.toArray) match {
                case Some(event: RingMinedEvent.Result) ⇒
                  splitEventToFills(event._fills)
                case _ ⇒
                  Seq.empty[String]
              }
            }
          }
        })
    } else {
      Seq.empty[String]
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

  def getXOrdersCancelledEvents(
      receipts: Seq[Option[XTransactionReceipt]]
    ): Seq[XOrdersCancelledEvent] = {

    if (receipts.forall(_.nonEmpty)) {
      receipts.flatMap(
        receipt ⇒
          receipt.get.logs.flatMap { log ⇒
            {
              loopringProtocolAbi
                .unpackEvent(log.data, log.topics.toArray) match {
                case Some(event: OrdersCancelledEvent.Result) ⇒
                  event._orderHashes
                    .map(orderHash ⇒ {
                      XOrdersCancelledEvent()
                        .withOrderHash(orderHash)
                        .withBlockHeight(
                          Numeric.toBigInt(receipt.get.blockNumber).longValue()
                        )
                        .withBrokerOrOwner(event.address)
                        .withTxHash(receipt.get.transactionHash)
                    })
                case _ ⇒
                  Seq.empty[XOrdersCancelledEvent]
              }
            }
          }
      )
    } else {
      Seq.empty[XOrdersCancelledEvent]
    }
  }

  def getXOrdersCutoffEvent(
      receipts: Seq[Option[XTransactionReceipt]]
    ): Seq[XOrdersCutoffEvent] = {
    receipts.flatMap { receipt ⇒
      receipt.get.logs.map { log ⇒
        loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
          case Some(event: AllOrdersCancelledEvent.Result) ⇒
            Some(
              XOrdersCutoffEvent()
                .withTxHash(receipt.get.transactionHash)
                .withBlockHeight(
                  Numeric.toBigInt(receipt.get.blockNumber).longValue()
                )
                .withBroker(event._broker)
                .withCutoff(event._cutoff.longValue())
            )
          case Some(event: AllOrdersCancelledByBrokerEvent.Result) ⇒
            Some(
              XOrdersCutoffEvent()
                .withTxHash(receipt.get.transactionHash)
                .withBlockHeight(
                  Numeric.toBigInt(receipt.get.blockNumber).longValue()
                )
                .withBroker(event._broker)
                .withOwner(event._owner)
                .withCutoff(event._cutoff.longValue())
            )
          case Some(event: AllOrdersCancelledForTradingPairEvent.Result) ⇒
            Some(
              XOrdersCutoffEvent()
                .withTxHash(receipt.get.transactionHash)
                .withBlockHeight(
                  Numeric.toBigInt(receipt.get.blockNumber).longValue()
                )
                .withBroker(event._broker)
                .withCutoff(event._cutoff.longValue())
                .withTradingPair(
                  convert2Hex(
                    Address(event._token1).toString,
                    Address(event._token2).toString
                  )
                )
            )
          case Some(
              event: AllOrdersCancelledForTradingPairByBrokerEvent.Result
              ) ⇒
            Some(
              XOrdersCutoffEvent()
                .withTxHash(receipt.get.transactionHash)
                .withBlockHeight(
                  Numeric.toBigInt(receipt.get.blockNumber).longValue()
                )
                .withBroker(event._broker)
                .withOwner(event._owner)
                .withCutoff(event._cutoff.longValue())
                .withTradingPair(
                  convert2Hex(
                    Address(event._token1).toString,
                    Address(event._token2).toString
                  )
                )
            )
          case _ ⇒
            None
        }
      }.filter(_.nonEmpty).flatten
    }
  }

  //等待定义Online Order 结构
  def getOnlineOrders( receipts: Seq[Option[XTransactionReceipt]]):Seq[XRawOrder] = {
    if(receipts.forall(_.nonEmpty)){
      receipts.flatMap(receipt ⇒ {
        receipt.get.logs.map{log ⇒
         loopringProtocolAbi.unpackEvent(log.data,log.topics.toArray) match {
           case Some(event:OrderSubmittedEvent.Result)⇒
              Some(extractOrderfromEvent(event))
           case _ ⇒
             None
         }
        }.filter(_.nonEmpty).flatten
      })
    }else{
      Seq.empty[XRawOrder]
    }
  }

  def extractOrderfromEvent(event:OrderSubmittedEvent.Result):XRawOrder = {

    // 去掉head 2 * 64
    val data = Numeric.cleanHexPrefix(event.orderData).substring(128)

    XRawOrder()
      .withOwner(Numeric.prependHexPrefix(data.substring(0,64)))
      .withTokenS(Numeric.prependHexPrefix(data.substring(64,64 *2)))
      .withTokenB(Numeric.prependHexPrefix(data.substring(64 *2,64 *3)))
      .withAmountS(Numeric.hexStringToByteArray(data.substring(64 *3 ,64 * 4)))
      .withAmountB(Numeric.hexStringToByteArray(data.substring(64 *4 ,64 * 5)))
      .withValidSince(Numeric.toBigInt(data.substring(64 *5 ,64 * 6)).intValue())
      .withParams(
        XRawOrder.Params()
          .withBroker(Numeric.prependHexPrefix(data.substring(64 * 6,64 *7)))
          .withOrderInterceptor(Numeric.prependHexPrefix(data.substring(64 * 7,64 *8)))
          .withWallet(Numeric.prependHexPrefix(data.substring(64 * 8,64 * 9)))
          .withValidUntil(Numeric.toBigInt(data.substring(64 *9 ,64 * 10)).intValue())
          .withAllOrNone(Numeric.toBigInt(data.substring(64 *10 ,64 * 11)).intValue() == 1)
          .withTokenStandardS(XTokenStandard.fromValue(Numeric.toBigInt(data.substring(64 *17 ,64 * 18)).intValue()))
          .withTokenStandardB(XTokenStandard.fromValue(Numeric.toBigInt(data.substring(64 *18 ,64 * 19)).intValue()))
          .withTokenStandardFee(XTokenStandard.fromValue(Numeric.toBigInt(data.substring(64 *19 ,64 * 20)).intValue())))
      .withHash(event.orderHash)
      .withFeeParams(
        XRawOrder.FeeParams()
          .withTokenFee(Numeric.prependHexPrefix(data.substring(64 * 11,64 * 12)))
          .withAmountFee(Numeric.hexStringToByteArray(data.substring(64 * 12,64 * 13)))
          .withTokenSFeePercentage(Numeric.toBigInt(data.substring(64 * 13,64 * 14)).intValue())
          .withTokenBFeePercentage(Numeric.toBigInt(data.substring(64 * 14,64 * 15)).intValue())
          .withTokenRecipient(Numeric.prependHexPrefix(data.substring(64 * 15,64 * 16)))
          .withWalletSplitPercentage(Numeric.toBigInt(data.substring(64 * 16,64 * 17)).intValue()))
      .withErc1400Params(
        XRawOrder.ERC1400Params()
          .withTrancheS(Numeric.prependHexPrefix(data.substring(64 * 20,64 * 21)))
          .withTrancheB(Numeric.prependHexPrefix(data.substring(64 * 21,64 * 22)))
          .withTransferDataS(Numeric.prependHexPrefix(data.substring(64 * 22,64 * 23)))
      )
  }

}
