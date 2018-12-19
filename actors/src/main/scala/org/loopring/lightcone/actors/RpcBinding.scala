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

trait RpcBinding extends JsonRpcModule {
  override val endpoint = "api"

  ifReceive[XGetOrderbook].thenReply[XOrderbook]("orderbook")

  ifReceive[XSubmitOrderReq].thenReply[XSubmitOrderRes]("submit_order")

  ifReceive[XCancelOrderReq].thenReply[XCancelOrderRes]("cancel_order")

  //Ethereum Query
  ifReceive[XGetAllowanceReq]
    .thenReply[XGetAllowanceRes]("get_allowance")
  ifReceive[XGetBalanceReq].thenReply[XGetBalanceRes]("get_balance")
  ifReceive[XGetBalanceAndAllowancesReq]
    .thenReply[XGetBalanceAndAllowancesRes]("get_balance_and_allowance")
  ifReceive[GetFilledAmountReq]
    .thenReply[GetFilledAmountRes]("get_filled_amount")
}
