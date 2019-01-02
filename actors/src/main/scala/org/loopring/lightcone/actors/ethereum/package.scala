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
import org.json4s.{DefaultFormats, NoTypeHints}
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto.{BatchCallContracts, _}
import org.web3j.utils.Numeric

package object ethereum {

  type ProtoBuf[T] = scalapb.GeneratedMessage with scalapb.Message[T]

  private[ethereum] case class JsonRpcReqWrapped(
      id: Int,
      jsonrpc: String = "2.0",
      method: String,
      params: Any) {
    private implicit val formats = Serialization.formats(NoTypeHints)
    def toProto = JsonRpc.Request(write(this))
  }

  private[ethereum] case class JsonRpcResWrapped(
      id: Any,
      jsonrpc: String = "2.0",
      result: Any,
      error: Option[JsonRpc.Error]
    )

  private[ethereum] object JsonRpcResWrapped {
    private implicit val formats = DefaultFormats

    def toJsonRpcResWrapped
      : PartialFunction[JsonRpc.Response, JsonRpcResWrapped] = {
      case j: JsonRpc.Response => parse(j.json).extract[JsonRpcResWrapped]
    }
  }

  private[ethereum] case class BatchMethod(
      id: Int,
      method: String,
      params: Seq[Any])

  val erc20Abi = ERC20ABI()
  val tradeHistoryAbi = TradeHistoryAbi()
  val ringSubmitterAbi = RingSubmitterAbi()

  implicit def getBalanceAndAllowanceToBatchReq(
      delegateAddress: Address,
      req: GetBalanceAndAllowances.Req
    ): BatchCallContracts.Req = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val allowanceCallReqs =
      batchErc20AllowanceReq(delegateAddress, owner, tokens)
    val balanceCallReqs = batchErc20BalanceReq(owner, tokens)

    BatchCallContracts.Req(allowanceCallReqs ++ balanceCallReqs)
  }

  implicit def getBalanceToBatchReq(
      req: GetBalance.Req
    ): BatchCallContracts.Req = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val balanceCallReqs = batchErc20BalanceReq(owner, tokens)
    BatchCallContracts.Req(balanceCallReqs)
  }

  implicit def getAllowanceToBatchReq(
      delegateAddress: Address,
      req: GetAllowance.Req
    ): BatchCallContracts.Req = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val allowanceCallReqs =
      batchErc20AllowanceReq(delegateAddress, owner, tokens)
    BatchCallContracts.Req(allowanceCallReqs)
  }

  implicit def getFilledAmountToBatchReq(
      tradeHistoryAddress: Address,
      req: GetFilledAmount.Req
    ): BatchCallContracts.Req = {
    val batchFilledAmountReqs =
      batchFilledAmountReq(tradeHistoryAddress, req.orderIds)
    BatchCallContracts.Req(batchFilledAmountReqs)
  }

  implicit def batchContractCallResToBalanceAndAllowance(
      address: String,
      tokens: Seq[String],
      batchRes: BatchCallContracts.Res
    ): GetBalanceAndAllowances.Res = {

    val allowances = batchRes.resps.filter(_.id % 2 == 0).map { res =>
      ByteString.copyFrom(Numeric.toBigInt(res.result).toByteArray)
    }
    val balances =
      batchRes.resps.filter(_.id % 2 == 1).map { res =>
        ByteString.copyFrom(Numeric.toBigInt(res.result).toByteArray)
      }
    val balanceAndAllowance = (balances zip allowances).map { ba =>
      BalanceAndAllowance(ba._1, ba._2)
    }
    GetBalanceAndAllowances.Res(address, (tokens zip balanceAndAllowance).toMap)
  }

  implicit def batchContractCallResToBalance(
      address: String,
      tokens: Seq[String],
      batchRes: BatchCallContracts.Res
    ): GetBalance.Res = {
    val balances = batchRes.resps.map { res =>
      ByteString.copyFrom(Numeric.toBigInt(res.result).toByteArray)
    }
    GetBalance.Res(address, (tokens zip balances).toMap)
  }

  implicit def batchContractCallResToAllowance(
      address: String,
      tokens: Seq[String],
      batchRes: BatchCallContracts.Res
    ): GetAllowance.Res = {
    val allowances = batchRes.resps.map { res =>
      ByteString.copyFrom(Numeric.toBigInt(res.result).toByteArray)
    }
    GetAllowance.Res(address, (tokens zip allowances).toMap)
  }

  implicit def packRingToInput(data: String): String = {
    ringSubmitterAbi.submitRing.pack(
      SubmitRingsFunction.Params(data = Numeric.hexStringToByteArray(data))
    )
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
      val param = TransactionParams(to = contractAddress.toString, data = data)
      EthCall.Req(orderHash._2, Some(param), tag)
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
      val param = TransactionParams(to = token._1.toString, data = data)
      EthCall.Req(token._2 * 2, Some(param), tag)
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
      val param = TransactionParams(to = token._1.toString, data = data)
      EthCall.Req(1 + token._2 * 2, Some(param), tag)
    }
  }
}
