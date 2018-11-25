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

// /*
//  * Copyright 2018 Loopring Foundation
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package org.loopgring.lightcone.actors.core

// import org.loopring.lightcone.actors.data._
// import org.loopring.lightcone.proto.actors._
// import org.loopring.lightcone.proto.core._
// import org.loopring.lightcone.core.data.Order
// import XErrorCode._
// import CoreActorsIntegrationCommonSpec._

// class CoreActorsIntegrationSpec_SigleOrderSubmission_FeeIsAnotherToken
//   extends CoreActorsIntegrationCommonSpec(XMarketId(GTO, WETH)) {

//   "submit a single order" must {
//     "succeed and make change to orderbook" in {
//       val order = XOrder(
//         id = "order",
//         tokenS = WETH_TOKEN.address,
//         tokenB = GTO_TOKEN.address,
//         tokenFee = LRC_TOKEN.address,
//         amountS = "50".zeros(18),
//         amountB = "10000".zeros(18),
//         amountFee = "10".zeros(18),
//         walletSplitPercentage = 0.2,
//         status = XOrderStatus.NEW
//       )

//       accountManagerActor1 ! XSubmitOrderReq(Some(order))

//       accountBalanceProbe.expectQuery(ADDRESS_1, WETH_TOKEN.address)
//       accountBalanceProbe.replyWith(WETH_TOKEN.address, "100".zeros(18), "100".zeros(18))

//       accountBalanceProbe.expectQuery(ADDRESS_1, LRC_TOKEN.address)
//       accountBalanceProbe.replyWith(LRC_TOKEN.address, "10".zeros(18), "10".zeros(18))

//       orderHistoryProbe.expectQuery(order.id)
//       orderHistoryProbe.replyWith(order.id, "0".zeros(0))

//       expectMsgPF() {
//         case XSubmitOrderRes(ERR_OK, Some(xorder)) ⇒
//           val order: Order = xorder
//           log.debug(s"order submitted: $order")
//         case XSubmitOrderRes(ERR_UNKNOWN, None) ⇒
//           log.debug(s"occurs ERR_UNKNOWN when submitting order:$order")
//       }

//       orderbookManagerActor ! XGetOrderbookReq(0, 100)

//       expectMsgPF() {
//         case a: XOrderbook ⇒
//           info("----orderbook: " + a)
//       }
//     }
//   }

// }
