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
      req: GetBurnRate.Req,
      contractAddress: Address,
      tag: String
    ): EthCall.Req = {
    val input = burnRateTableAbi.getBurnRate.pack(
      GetBurnRateFunction.Params(
        token = req.token
      )
    )
    val param = TransactionParams(to = contractAddress.toString, data = input)
    EthCall.Req(param = Some(param), tag = tag)
  }

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
      req: GetCutoff.Req,
      contractAddress: Address,
      tag: String
    ): EthCall.Req = {
    val input = req match {
      case GetCutoff.Req(broker, "", "") =>
        tradeHistoryAbi.cutoffForBroker.pack(
          CutoffForBrokerFunction.Params(broker)
        )

      case GetCutoff.Req(broker, owner, "") =>
        tradeHistoryAbi.cutoffForOwner.pack(
          CutoffForOwnerFunction.Params(broker, owner)
        )

      case GetCutoff.Req(broker, "", tokenPair) =>
        tradeHistoryAbi.cutoffForTradingPairBroker.pack(
          CutoffForTradingPairBrokerFunction
            .Params(broker, Numeric.hexStringToByteArray(tokenPair))
        )

      case GetCutoff.Req(broker, owner, tokenPair) =>
        tradeHistoryAbi.cutoffForTradingPairOwner.pack(
          CutoffForTradingPairOwnerFunction
            .Params(broker, owner, Numeric.hexStringToByteArray(tokenPair))
        )
    }

    val param = TransactionParams(to = contractAddress.toString, data = input)
    EthCall.Req(param = Some(param), tag = tag)
  }

}
