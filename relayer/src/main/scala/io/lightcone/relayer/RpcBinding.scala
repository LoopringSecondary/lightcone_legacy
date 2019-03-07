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
import io.lightcone.relayer.data._

// Owner: Hongyu
trait RpcBinding extends JsonRpcModule {

  method("get_orderbook")
  // .accepts[ext.GetOrderbook.Req, GetOrderbook.Req]
  // .replies[GetOrderbook.Res, ext.GetOrderbook.Res]
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
    .accepts[GetOrders.Req]
    .replies[GetOrders.Res]

  method("get_fills")
    .accepts[GetFills.Req]
    .replies[GetFills.Res]

  method("get_rings")
    .accepts[GetRings.Req]
    .replies[GetRings.Res]

  method("get_tokens")
    .accepts[GetTokens.Req]
    .replies[GetTokens.Res]

  method("get_markets")
    .accepts[GetMarkets.Req]
    .replies[GetMarkets.Res]

  method("get_market_history")
    .accepts[GetMarketHistory.Req]
    .replies[GetMarketHistory.Res]

  method("get_activities")
    .accepts[GetActivities.Req]
    .replies[GetActivities.Res]

  //Ethereum Query
  method("get_account")
    .accepts[GetAccount.Req]
    .replies[GetAccount.Res]

  method("get_accounts")
    .accepts[GetAccounts.Req]
    .replies[GetAccounts.Res]

  method("get_filled_amount")
    .accepts[GetFilledAmount.Req]
    .replies[GetFilledAmount.Res]

}
