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

package io.lightcone.relayer

import io.lightcone.core.RawOrder
import io.lightcone.relayer.data._
import io.lightcone.relayer.jsonrpc.Linter

object RpcDataLinters {

  implicit val submitOrderResLinter = new Linter[SubmitOrder.Res] {

    def lint(data: SubmitOrder.Res) = SubmitOrder.Res(success = data.success)
  }
  implicit val GetGetOrdersResLinter = new Linter[GetOrders.Res] {

    def lint(data: GetOrders.Res) =
      data.copy(orders = data.orders.map(cleanRawOrder))
  }

  def cleanRawOrder(order: RawOrder): RawOrder =
    RawOrder(
      hash = order.hash,
      version = order.version,
      owner = order.owner,
      tokenS = order.tokenS,
      tokenB = order.tokenB,
      amountS = order.amountS,
      amountB = order.amountB,
      validSince = order.validSince,
      params = order.params.map(
        param =>
          RawOrder.Params(
            broker = param.broker,
            orderInterceptor = param.orderInterceptor,
            wallet = param.wallet,
            validUntil = param.validUntil,
            allOrNone = param.allOrNone
          )
      ),
      feeParams = order.feeParams.map(
        param =>
          RawOrder.FeeParams(
            tokenFee = param.tokenFee,
            amountFee = param.amountFee,
            tokenSFeePercentage = param.tokenSFeePercentage,
            tokenBFeePercentage = param.tokenBFeePercentage,
            tokenRecipient = param.tokenRecipient
          )
      ),
      state = order.state
    )

}
