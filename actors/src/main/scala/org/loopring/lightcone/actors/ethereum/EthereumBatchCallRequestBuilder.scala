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

package org.loopring.lightcone.actors.ethereum

import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

class EthereumBatchCallRequestBuilder {

  def buildRequest(
      delegateAddress: Address,
      req: GetBalanceAndAllowances.Req,
      tag: String
    ): BatchCallContracts.Req = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val allowanceCallReqs =
      buildBatchErc20AllowanceReq(delegateAddress, owner, tokens, tag)
    val balanceCallReqs = buildBatchErc20BalanceReq(owner, tokens, tag)

    BatchCallContracts.Req(allowanceCallReqs ++ balanceCallReqs)
  }

  def buildRequest(
      req: GetBalance.Req,
      tag: String
    ): BatchCallContracts.Req = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val balanceCallReqs = buildBatchErc20BalanceReq(owner, tokens, tag)
    BatchCallContracts.Req(balanceCallReqs)
  }

  def buildRequest(
      delegateAddress: Address,
      req: GetAllowance.Req,
      tag: String
    ): BatchCallContracts.Req = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val allowanceCallReqs =
      buildBatchErc20AllowanceReq(delegateAddress, owner, tokens, tag)
    BatchCallContracts.Req(allowanceCallReqs)
  }

  def buildRequest(
      tradeHistoryAddress: Address,
      req: GetFilledAmount.Req,
      tag: String
    ): BatchCallContracts.Req = {
    val batchFilledAmountReqs =
      buildBatchFilledAmountReq(tradeHistoryAddress, req.orderIds, tag)
    BatchCallContracts.Req(batchFilledAmountReqs)
  }

  def buildRequest(
      delegateAddress: Address,
      addresses: Seq[AllowanceUpdatedAddress],
      tag: String
    ): BatchCallContracts.Req = {
    val ethCallReqs = addresses.zipWithIndex.map(item => {
      val (address, index) = item
      val data = erc20Abi.allowance.pack(
        AllowanceFunction
          .Parms(_spender = delegateAddress.toString(), _owner = address.owner)
      )
      val param = TransactionParams(to = address.token, data = data)
      EthCall.Req(index, Some(param), tag)
    })
    BatchCallContracts.Req(ethCallReqs)
  }

  def buildRequest(
      addresses: Seq[BalanceUpdatedAddress],
      tag: String
    ): BatchCallContracts.Req = {
    val ethCallReqs = addresses.zipWithIndex.map(item => {
      val (address, index) = item
      val data = erc20Abi.balanceOf.pack(
        BalanceOfFunction
          .Parms(_owner = address.owner)
      )
      val param = TransactionParams(to = address.token, data = data)
      EthCall.Req(index, Some(param), tag)
    })
    BatchCallContracts.Req(ethCallReqs)
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
    ): Seq[EthCall.Req] = {
    orderHashes.zipWithIndex.map { orderHash =>
      val data = tradeHistoryAbi.filled.pack(
        FilledFunction.Params(Numeric.hexStringToByteArray(orderHash._1))
      )
      val param = TransactionParams(to = contractAddress.toString, data = data)
      EthCall.Req(orderHash._2, Some(param), tag)
    }
  }
}
