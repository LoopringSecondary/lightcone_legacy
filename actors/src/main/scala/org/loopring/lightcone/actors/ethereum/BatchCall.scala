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

import com.google.protobuf.ByteString
import org.loopring.lightcone.ethereum.abi.{
  AllowanceFunction,
  BalanceOfFunction,
  FilledFunction
}
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

object BatchCall {

  def from(
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

  def from(req: GetBalance.Req): BatchCallContracts.Req = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val balanceCallReqs = batchErc20BalanceReq(owner, tokens)
    BatchCallContracts.Req(balanceCallReqs)
  }

  def from(
      delegateAddress: Address,
      req: GetAllowance.Req
    ): BatchCallContracts.Req = {
    val owner = Address(req.address)
    val tokens = req.tokens.map(Address(_))
    val allowanceCallReqs =
      batchErc20AllowanceReq(delegateAddress, owner, tokens)
    BatchCallContracts.Req(allowanceCallReqs)
  }

  def from(
      tradeHistoryAddress: Address,
      req: GetFilledAmount.Req
    ): BatchCallContracts.Req = {
    val batchFilledAmountReqs =
      batchFilledAmountReq(tradeHistoryAddress, req.orderIds)
    BatchCallContracts.Req(batchFilledAmountReqs)
  }

  def toBalanceAndAllowance(
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

  def toBalance(
      address: String,
      tokens: Seq[String],
      batchRes: BatchCallContracts.Res
    ): GetBalance.Res = {
    val balances = batchRes.resps.map { res =>
      ByteString.copyFrom(Numeric.toBigInt(res.result).toByteArray)
    }
    GetBalance.Res(address, (tokens zip balances).toMap)
  }

  def toAllowance(
      address: String,
      tokens: Seq[String],
      batchRes: BatchCallContracts.Res
    ): GetAllowance.Res = {
    val allowances = batchRes.resps.map { res =>
      ByteString.copyFrom(Numeric.toBigInt(res.result).toByteArray)
    }
    GetAllowance.Res(address, (tokens zip allowances).toMap)
  }

  private def batchErc20AllowanceReq(
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

  private def batchErc20BalanceReq(
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
}
