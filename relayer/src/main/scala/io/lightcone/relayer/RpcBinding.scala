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

import io.lightcone.relayer.jsonrpc.JsonRpcModule
import io.lightcone.relayer.rpc.RpcDataConversions._
import io.lightcone.proto._
import io.lightcone.core._

// Owner: Hongyu
trait RpcBinding extends JsonRpcModule {
  method("get_orderbook")
  // .accepts[rpcdata.GetOrderbook.Req, GetOrderbook.Req]
  // .replies[GetOrderbook.Res, rpcdata.GetOrderbook.Res]
    .accepts[GetOrderbook.Req]
    .replies[GetOrderbook.Res]

  method("submit_order") //
    .accepts[SubmitOrder.Req] //
    .replies[SubmitOrder.Res]

  method("cancel_order") //
    .accepts[CancelOrder.Req] //
    .replies[CancelOrder.Res]

  // // db query
  method("get_orders")
    .accepts[GetOrdersForUser.Req]
    .replies[GetOrdersForUser.Res]

  method("get_trades")
    .accepts[GetTrades.Req]
    .replies[GetTrades.Res]

  method("get_rings")
    .accepts[GetRings.Req]
    .replies[GetRings.Res]

  method("get_transactions")
    .accepts[GetTransactionRecords.Req]
    .replies[GetTransactionRecords.Res]

  method("get_transaction_count")
    .accepts[GetTransactionRecordCount.Req]
    .replies[GetTransactionRecordCount.Res]

  method("get_metadatas")
    .accepts[GetMetadatas.Req]
    .replies[GetMetadatas.Res]

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
