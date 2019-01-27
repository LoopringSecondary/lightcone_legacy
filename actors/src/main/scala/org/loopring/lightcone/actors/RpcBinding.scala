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

import org.loopring.lightcone.actors.jsonrpc.JsonRpcModule
import org.loopring.lightcone.proto._

// Owner: Hongyu
trait RpcBinding extends JsonRpcModule {

  "get_orderbook"
    .accepts[GetOrderbook.Req, GetOrderbook.Req]
    .replies[GetOrderbook.Res, GetOrderbook.Res]

  "submit_order"
    .accepts[SubmitOrder.Req, SubmitOrder.Req]
    .replies[SubmitOrder.Res, SubmitOrder.Res]

  "cancel_order"
    .accepts[CancelOrder.Req, CancelOrder.Req]
    .replies[CancelOrder.Res, CancelOrder.Res]

  // db query
  "get_orders"
    .accepts[GetOrdersForUser.Req, GetOrdersForUser.Req]
    .replies[GetOrdersForUser.Res, GetOrdersForUser.Res]

  "get_trades"
    .accepts[GetTrades.Req, GetTrades.Req]
    .replies[GetTrades.Res, GetTrades.Res]

  "get_transactions"
    .accepts[GetTransactionRecords.Req, GetTransactionRecords.Req]
    .replies[GetTransactionRecords.Res, GetTransactionRecords.Res]

  "get_transaction_count"
    .accepts[GetTransactionRecordCount.Req, GetTransactionRecordCount.Req]
    .replies[GetTransactionRecordCount.Res, GetTransactionRecordCount.Res]

  "get_metadatas"
    .accepts[GetMetadatas.Req, GetMetadatas.Req]
    .replies[GetMetadatas.Res, GetMetadatas.Res]

  //Ethereum Query
  "get_allowance"
    .accepts[GetAllowance.Req, GetAllowance.Req]
    .replies[GetAllowance.Res, GetAllowance.Res]

  "get_balance"
    .accepts[GetBalance.Req, GetBalance.Req]
    .replies[GetBalance.Res, GetBalance.Res]

  "get_balance_and_allowance"
    .accepts[GetBalanceAndAllowances.Req, GetBalanceAndAllowances.Req]
    .replies[GetBalanceAndAllowances.Res, GetBalanceAndAllowances.Res]

  "get_filled_amount"
    .accepts[GetFilledAmount.Req, GetFilledAmount.Req]
    .replies[GetFilledAmount.Res, GetFilledAmount.Res]

}
