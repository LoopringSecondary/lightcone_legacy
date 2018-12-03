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
import org.loopring.lightcone.proto.actors.XErrorCode._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._

class CoreActorsIntegrationSpec_AllowanceUpdate
  extends CoreActorsIntegrationCommonSpec(XMarketId(GTO_TOKEN.address, WETH_TOKEN.address)) {

  "update allowance after submit an order" must {
    "received by marketManager, orderbookManager if the actual changed" in {
      val order = XOrder(
        id = "order",
        tokenS = WETH_TOKEN.address,
        tokenB = GTO_TOKEN.address,
        tokenFee = LRC_TOKEN.address,
        amountS = "50".zeros(18),
        amountB = "10000".zeros(18),
        amountFee = "10".zeros(18),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.STATUS_NEW
      )

      accountManagerActor1 ! XSubmitOrderReq(Some(order))

      accountBalanceProbe.expectQuery(ADDRESS_1, WETH_TOKEN.address)
      accountBalanceProbe.replyWith(ADDRESS_1, WETH_TOKEN.address, "100".zeros(18), "100".zeros(18))

      accountBalanceProbe.expectQuery(ADDRESS_1, LRC_TOKEN.address)
      accountBalanceProbe.replyWith(ADDRESS_1, LRC_TOKEN.address, "10".zeros(18), "10".zeros(18))

      ordersDalActorProbe.expectQuery(order.id)
      ordersDalActorProbe.replyWith(order.id, "0".zeros(0))

      expectMsgPF() {
        case XSubmitOrderRes(ERR_OK, Some(xorder)) ⇒
          val order: Order = xorder
          info(s"submitted an order: $order")
        case XSubmitOrderRes(ERR_UNKNOWN, None) ⇒
          info(s"occurs ERR_UNKNOWN when submitting order:$order")
      }

      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          a.sells.nonEmpty should be(true)
          a.sells.head.amount should be("50.00")
          info("----orderbook status after submit an order: " + a)
      }

      accountManagerActor1 ! XAddressAllowanceUpdated(ADDRESS_1, WETH_TOKEN.address, "45".zeros(18))

      //等待accountManager执行完毕
      Thread.sleep(1000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          a.sells.nonEmpty should be(true)
          a.sells.head.amount should be("45.00")
          info("----orderbook status after cancel this order: " + a)
      }
    }
  }
}
