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
import io.lightcone.relayer.jsonrpc.JsonRpcModule
import io.lightcone.relayer.data._
import RpcDataConversions._

// Owner: Hongyu
trait RpcBinding extends JsonRpcModule {

  method("get_order_book")
    .accepts[GetOrderbook.Req]
    .replies[GetOrderbook.Res]

  method("submit_order") //
    .accepts[RawOrder, SubmitOrder.Req] //
    .replies[SubmitOrder.Res, rpc.SubmitOrder.Result]

  method("cancel_order") // TODO 需要验签支持，目前结构中没有sig字段
    .accepts[CancelOrder.Req] //
    .replies[CancelOrder.Res]

  // // db query
  method("get_orders") //TODO GetOrdersForUser.Res 中缺少pageNum 和 pageSize
    .accepts[rpc.GetOrders.Params, GetOrdersForUser.Req]
    .replies[GetOrdersForUser.Res]

  method("get_trades") //TODO GetTrades.Res 中缺少pageNum 和 pageSize
    .accepts[rpc.GetTrades.Params, GetTrades.Req]
    .replies[GetTrades.Res]

  method("get_rings") //TODO GetRings.Res 中缺少pageNum 和 pageSize
    .accepts[rpc.GetRings.Params, GetRings.Req]
    .replies[GetRings.Res]

  method("get_transactions") //TODO GetTransactionRecords.Res 中缺少pageNum, pageSize 和 total
    .accepts[rpc.GetTransactions.Params, GetTransactionRecords.Req]
    .replies[GetTransactionRecords.Res]

  //TODO 需要修改成get_nonce,暂未实现
  method("get_transaction_count")
    .accepts[GetTransactionRecordCount.Req]
    .replies[GetTransactionRecordCount.Res]

  //TODO 前端暴露get_tokens 和 get_markets 接口,该接口还保留吗?
  method("get_metadatas")
    .accepts[GetMetadatas.Req]
    .replies[GetMetadatas.Res]

  method("get_tokens")
    .accepts[GetMetadatas.Req]
    .replies[GetMetadatas.Res, rpc.GetTokens.Result]

  method("get_market")
    .accepts[GetMetadatas.Req]
    .replies[GetMetadatas.Res, rpc.GetMarkets.Result]

  //Ethereum Query
  method("get_allowance")
    .accepts[GetAllowance.Req]
    .replies[GetAllowance.Res]

  method("get_balance") //
    .accepts[GetBalance.Req] //
    .replies[GetBalance.Res]

  method("get_balance_and_allowance")
    .accepts[GetBalanceAndAllowances.Req]
    .replies[GetBalanceAndAllowances.Res]

  method("get_filled_amount")
    .accepts[GetFilledAmount.Req]
    .replies[GetFilledAmount.Res]

}
