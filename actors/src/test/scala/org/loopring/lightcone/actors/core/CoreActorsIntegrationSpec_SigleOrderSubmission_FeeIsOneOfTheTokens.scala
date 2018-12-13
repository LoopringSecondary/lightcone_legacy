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

package org.loopring.lightcone.actors.core

import org.loopring.lightcone.actors.core.CoreActorsIntegrationCommonSpec._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.proto._

// class CoreActorsIntegrationSpec_SigleOrderSubmission_FeeIsOneOfTheTokens
//   extends CoreActorsIntegrationCommonSpec(
//     XMarketId(LRC, WETH),
//     """
//     weth {
//       address = "something"
//     }
//     loopring-protocol {
//       address = "0x8d8812b72d1e4ffCeC158D25f56748b7d67c1e78",
//       delegate-address ="0x17233e07c67d086464fD408148c3ABB56245FA64"
//       gas-limit-per-ring-v2 = "1000000"
//     }
//     account_manager {
//       skip-recovery = yes
//       recover-batch-size = 2
//     }
//     market_manager {
//       skip-recovery = yes
//       price-decimals = 5
//       recover-batch-size = 5
//     }
//     orderbook_manager {
//       levels = 2
//       price-decimals = 5
//       precision-for-amount = 2
//       precision-for-total = 1
//     }
//     ring_settlement {
//       submitter-private-key = "0xa1"
//     }
//     gas_price {
//       default = "10000000000"
//     }
//     """) {

//   "submit a single order" must {
//     "succeed and make change to orderbook" in {
//       val order = XOrder(
//         id = "buy_lrc",
//         tokenS = WETH_TOKEN.address,
//         tokenB = LRC_TOKEN.address,
//         tokenFee = LRC_TOKEN.address,
//         amountS = "50".zeros(18),
//         amountB = "10000".zeros(18),
//         amountFee = "10".zeros(18),
//         status = XOrderStatus.STATUS_NEW
//       )

//       accountManagerActor1 ! XSubmitOrderReq(Some(order))

//       accountBalanceProbe.expectQuery(ADDRESS_1, WETH_TOKEN.address)
//       accountBalanceProbe.replyWith(ADDRESS_1, WETH_TOKEN.address, "100".zeros(18), "100".zeros(18))

//       accountBalanceProbe.expectQuery(ADDRESS_1, LRC_TOKEN.address)
//       accountBalanceProbe.replyWith(ADDRESS_1, LRC_TOKEN.address, "0".zeros(0), "0".zeros(0))

//       orderHistoryProbe.expectQuery(order.id)
//       orderHistoryProbe.replyWith(order.id, "0".zeros(0))

//       expectMsgPF() {
//         case XSubmitOrderRes(ERR_OK, Some(xorder)) ⇒
//           val order: Order = xorder
//           log.debug(s"order submitted: $order")
//         case XSubmitOrderRes(ERR_INTERNAL_UNKNOWN, None) ⇒
//       }

//       orderbookManagerActor ! XGetOrderbookReq(0, 100)

//       expectMsgPF() {
//         case a: XOrderbook ⇒
//           log.debug("----orderbook: " + a)
//       }
//     }
//   }
// }
