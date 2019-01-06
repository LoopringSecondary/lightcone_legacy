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

class EthereumCallRequestBuilder {

  def buildRequest(
      req: GetOrderCancellation.Req,
      contractAddress: Address,
      tag: String
    ): EthCall.Req = {
    val input = tradeHistoryAbi.cancelled.pack(
      CancelledFunction.Params(
        broker = req.broker,
        orderHash = Numeric.hexStringToByteArray(req.orderHash)
      )
    )
    val param = TransactionParams(to = contractAddress.toString, data = input)
    EthCall.Req(param = Some(param), tag = tag)
  }

  def buildRequest(
      req: GetCutoffForTradingPairBroker.Req,
      contractAddress: Address,
      tag: String
    ): EthCall.Req = {
    val input = tradeHistoryAbi.cutoffForTradingPairBroker.pack(
      CutoffForTradingPairBrokerFunction.Params(
        broker = req.broker,
        tokenPair = Numeric.hexStringToByteArray(req.tokenPair)
      )
    )
    val param = TransactionParams(to = contractAddress.toString, data = input)
    EthCall.Req(param = Some(param), tag = tag)
  }

  def buildRequest(
      req: GetCutoffForTradingPairOwner.Req,
      contractAddress: Address,
      tag: String
    ): EthCall.Req = {
    val input = tradeHistoryAbi.cutoffForTradingPairOwner.pack(
      CutoffForTradingPairOwnerFunction.Params(
        broker = req.broker,
        owner = req.owner,
        tokenPair = Numeric.hexStringToByteArray(req.tokenPair)
      )
    )
    val param = TransactionParams(to = contractAddress.toString, data = input)
    EthCall.Req(param = Some(param), tag = tag)
  }

  def buildRequest(
      req: GetCutoffForOwner.Req,
      contractAddress: Address,
      tag: String
    ): EthCall.Req = {
    val input = tradeHistoryAbi.cutoffForOwner.pack(
      CutoffForOwnerFunction.Params(broker = req.broker, owner = req.owner)
    )
    val param = TransactionParams(to = contractAddress.toString, data = input)
    EthCall.Req(param = Some(param), tag = tag)
  }

  def buildRequest(
      req: GetCutoffForBroker.Req,
      contractAddress: Address,
      tag: String
    ): EthCall.Req = {
    val input =
      tradeHistoryAbi.cutoffForBroker.pack(
        CutoffForBrokerFunction.Params(broker = req.broker)
      )
    val param = TransactionParams(to = contractAddress.toString, data = input)
    EthCall.Req(param = Some(param), tag = tag)
  }

}
