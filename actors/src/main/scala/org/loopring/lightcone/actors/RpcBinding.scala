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
  // ifReceive[RequestType].thenReply[ResponseType]("method_name")
  // Note that RequestType and ResponseType must be proto messages.
  ifReceive[XRawOrder].thenReply[XRawOrder]("jsonrpc_method_name")

  //Order
  ifReceive[XSubmitRawOrderReq]
    .thenReply[XSubmitOrderRes]("submitOrder")
  ifReceive[XCancelOrderReq].thenReply[XCancelOrderRes]("cancelOrder")

  // OrderBook
  ifReceive[XGetOrderbookReq].thenReply[XOrderbook]("orderbook")

  //Ethereum Query
  ifReceive[XGetAllowanceReq]
    .thenReply[XGetAllowanceRes]("getAllowance")
  ifReceive[XGetBalanceReq].thenReply[XGetBalanceRes]("getBalance")
  ifReceive[XGetBalanceAndAllowancesReq]
    .thenReply[XGetBalanceAndAllowancesRes]("getBalanceAndAllowance")
  ifReceive[GetFilledAmountReq]
    .thenReply[GetFilledAmountRes]("getFilledAmount")
}
