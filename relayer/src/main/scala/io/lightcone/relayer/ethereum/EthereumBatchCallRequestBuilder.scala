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

package io.lightcone.relayer.ethereum

import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.event._
import io.lightcone.relayer.data._
import io.lightcone.lib._
import org.web3j.utils.Numeric

// Owner: Yadong
class EthereumBatchCallRequestBuilder {

  def buildRequest(
      delegateAddress: Address,
      req: GetBalanceAndAllowances.Req
    ): BatchCallContracts.Req = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val allowanceCallReqs =
      buildBatchErc20AllowanceReq(delegateAddress, owner, tokens, req.tag)
    val balanceCallReqs = buildBatchErc20BalanceReq(owner, tokens, req.tag)

    BatchCallContracts.Req(
      allowanceCallReqs ++ balanceCallReqs,
      returnBlockNum(req.tag)
    )
  }

  def buildRequest(req: GetBalance.Req): BatchCallContracts.Req = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val balanceCallReqs = buildBatchErc20BalanceReq(owner, tokens, req.tag)
    BatchCallContracts.Req(
      balanceCallReqs,
      returnBlockNum(req.tag)
    )
  }

  def buildRequest(
      delegateAddress: Address,
      req: GetAllowance.Req
    ): BatchCallContracts.Req = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val allowanceCallReqs =
      buildBatchErc20AllowanceReq(delegateAddress, owner, tokens, req.tag)
    BatchCallContracts.Req(
      allowanceCallReqs,
      returnBlockNum(req.tag)
    )
  }

  def buildRequest(
      tradeHistoryAddress: Address,
      req: GetFilledAmount.Req
    ): BatchCallContracts.Req = {
    val batchFilledAmountReqs =
      buildBatchFilledAmountReq(tradeHistoryAddress, req.orderIds, req.tag)
    BatchCallContracts.Req(
      batchFilledAmountReqs,
      returnBlockNum(req.tag)
    )
  }

  def buildRequest(
      addresses: Seq[AddressBalanceUpdatedEvent],
      tag: String
    ): BatchCallContracts.Req = {
    val balanceCallReqs = addresses.zipWithIndex.map {
      case (address, index) =>
        val data = erc20Abi.balanceOf.pack(
          BalanceOfFunction.Parms(_owner = address.address.toString)
        )
        val param = TransactionParams(to = address.token, data = data)
        EthCall.Req(index, Some(param), tag)
    }
    BatchCallContracts.Req(
      balanceCallReqs,
      returnBlockNum(tag)
    )
  }

  def buildRequest(
      delegateAddress: Address,
      addresses: Seq[AddressAllowanceUpdatedEvent],
      tag: String
    ): BatchCallContracts.Req = {
    val balanceCallReqs = addresses.zipWithIndex.map {
      case (address, index) =>
        val data = erc20Abi.allowance.pack(
          AllowanceFunction.Parms(
            _owner = address.address,
            _spender = delegateAddress.toString()
          )
        )
        val param = TransactionParams(to = address.token, data = data)
        EthCall.Req(index, Some(param), tag)
    }
    BatchCallContracts.Req(
      balanceCallReqs,
      returnBlockNum(tag)
    )
  }

  def buildRequest(
      req: BatchGetCutoffs.Req,
      tradeHistoryAddress: Address
    )(
      implicit
      rb: EthereumCallRequestBuilder
    ): BatchCallContracts.Req = {
    val cutoffCallReqs = req.reqs.map { cutoffReq =>
      rb.buildRequest(cutoffReq, tradeHistoryAddress).reqs.head
    }
    BatchCallContracts.Req(cutoffCallReqs, returnBlockNum(req.tag))
  }

  private def buildBatchErc20AllowanceReq(
      delegateAddress: Address,
      owner: Address,
      tokens: Seq[Address],
      tag: String = "latest"
    ): Seq[EthCall.Req] = {
    tokens.zipWithIndex.map(token => {
      val data = erc20Abi.allowance.pack(
        AllowanceFunction
          .Parms(_spender = delegateAddress.toString, _owner = owner.toString)
      )
      val param = TransactionParams(to = token._1.toString, data = data)
      EthCall.Req(token._2 * 2, Some(param), tag)
    })
  }

  private def buildBatchErc20BalanceReq(
      owner: Address,
      tokens: Seq[Address],
      tag: String = "latest"
    ): Seq[EthCall.Req] = {
    tokens.zipWithIndex.map { token =>
      val data = erc20Abi.balanceOf.pack(
        BalanceOfFunction.Parms(_owner = owner.toString)
      )
      val param = TransactionParams(to = token._1.toString, data = data)
      EthCall.Req(1 + token._2 * 2, Some(param), tag)
    }
  }

  private def buildBatchFilledAmountReq(
      contractAddress: Address,
      orderHashes: Seq[String],
      tag: String = "latest"
    ) = {
    orderHashes.zipWithIndex.map { orderHash =>
      val data = tradeHistoryAbi.filled.pack(
        FilledFunction.Params(Numeric.hexStringToByteArray(orderHash._1))
      )
      val param = TransactionParams(to = contractAddress.toString, data = data)
      EthCall.Req(orderHash._2, Some(param), tag)
    }
  }

  @inline def returnBlockNum(tag: String) = tag.isEmpty || tag == "latest"
}
