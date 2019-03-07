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

object RpcDataConversions {
  //  implicit def convertGetOrderbookReq(
  //      r: ext.GetOrderbook.Req
  //    ): GetOrderbook.Req =
  //    null
  //
  //  implicit def convertGetOrderbookRes(
  //      r: GetOrderbook.Res
  //    ): ext.GetOrderbook.Res =
  //    null

  // TODO(yadong): def an implicit method as following and it will be
  // called automatically by the RpcBinding.
  implicit def cleanGetOrderboobRes(res: GetOrderbook.Res) =
    new GetOrderbook.Res( /* select some fields only */ )

  implicit def cleanSubmitOrderRes(res: SubmitOrder.Res) =
    SubmitOrder.Res(success = res.success)

  implicit def cleanGetOrdersRes(res: GetOrders.Res) =
    res.copy(orders = res.orders.map(cleanRawOrder))

  implicit def cleanRawOrder(order: RawOrder): RawOrder =
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
