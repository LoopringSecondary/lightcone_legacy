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

  ifReceive[GetOrderbook.Req].thenReply[GetOrderbook.Res]("orderbook")

  ifReceive[SubmitOrder.Req].thenReply[SubmitOrder.Res]("submit_order")

  ifReceive[CancelOrder.Req].thenReply[CancelOrder.Res]("cancel_order")

  // db query
  ifReceive[GetOrdersForUser.Req]
    .thenReply[GetOrdersForUser.Res]("get_orders")

  ifReceive[GetTrades.Req]
    .thenReply[GetTrades.Res]("get_trades")

  //Ethereum Query
  ifReceive[GetAllowance.Req]
    .thenReply[GetAllowance.Res]("get_allowance")

  ifReceive[GetBalance.Req].thenReply[GetBalance.Res]("get_balance")

  ifReceive[GetBalanceAndAllowances.Req]
    .thenReply[GetBalanceAndAllowances.Res]("get_balance_and_allowance")

  ifReceive[GetFilledAmount.Req]
    .thenReply[GetFilledAmount.Res]("get_filled_amount")

  ifReceive[GetTransactions.Req]
    .thenReply[GetTransactions.Res]("get_transactions")
  ifReceive[GetTransactionCount.Req]
    .thenReply[GetTransactionCount.Res]("get_transaction_count")

}
