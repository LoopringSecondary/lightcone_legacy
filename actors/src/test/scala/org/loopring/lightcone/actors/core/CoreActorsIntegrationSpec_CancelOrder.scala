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
import org.loopring.lightcone.proto.actors.XErrorCode.{ ERR_OK, ERR_UNKNOWN }
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._

class CoreActorsIntegrationSpec_CancelOrder
  extends CoreActorsIntegrationCommonSpec(
    XMarketId(GTO_TOKEN.address, WETH_TOKEN.address),
    """
    account_manager {
      skip-recovery = yes
      recover-batch-size = 2
    }
    market_manager {
      skip-recovery = yes
      price-decimals = 5
      recover-batch-size = 5
    }
    orderbook_manager {
      levels = 2
      price-decimals = 5
      precision-for-amount = 2
      precision-for-total = 1
    }
    ring_settlement {
      submitter-private-key = "0xa1"
    }
    """
  ) {

  "cancel an order to generate a cancel event" must {
    "received by marketManager, orderbookManager" in {
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

      orderHistoryProbe.expectQuery(order.id)
      orderHistoryProbe.replyWith(order.id, "0".zeros(0))

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
          info("----orderbook status after submit an order: " + a)
      }

      accountManagerActor1 ! XCancelOrderReq(order.id, false)
      expectMsgPF() {
        case res: XCancelOrderRes ⇒
          info(s"-- canceled this order: $res")
      }

      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after cancel this order: " + a)
      }
    }
  }
  "cancel an order that not existed" must {
    "return ERR_ORDER_NOT_EXIST" in {

      accountManagerActor1 ! XCancelOrderReq("not-exists-order-id", false)
      expectMsgPF() {
        case res: XCancelOrderRes ⇒
          info(s"-- canceled this order: $res")
          res.error should be(XErrorCode.ERR_ORDER_NOT_EXIST)
      }

      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after cancel this order: " + a)
      }
    }
  }
}
