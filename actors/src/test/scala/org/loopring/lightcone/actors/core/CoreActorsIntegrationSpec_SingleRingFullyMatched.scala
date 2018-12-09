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
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._

import scala.concurrent.duration._

class CoreActorsIntegrationSpec_SingleRingFullyMatched
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

  "submiting two orders with exact the same price and amount" must {
    "generate ring then send events to settlement, orderbookManager, ethereum" in {
      val maker1 = XOrder(
        id = "maker1",
        tokenS = WETH_TOKEN.address,
        tokenB = GTO_TOKEN.address,
        tokenFee = LRC_TOKEN.address,
        amountS = "10".zeros(18),
        amountB = "100".zeros(10),
        amountFee = "10".zeros(18),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.STATUS_NEW,
        reserved = Some(XOrderState("10".zeros(18), "100".zeros(10), "10".zeros(18))),
        outstanding = Some(XOrderState("10".zeros(18), "100".zeros(10), "10".zeros(18))),
        actual = Some(XOrderState("10".zeros(18), "100".zeros(10), "10".zeros(18))),
        matchable = Some(XOrderState("10".zeros(18), "100".zeros(10), "10".zeros(18)))
      )
      val taker1 = XOrder(
        id = "taker1",
        tokenS = GTO_TOKEN.address,
        tokenB = WETH_TOKEN.address,
        tokenFee = LRC_TOKEN.address,
        amountS = "100".zeros(10),
        amountB = "10".zeros(18),
        amountFee = "10".zeros(18),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.STATUS_NEW,
        outstanding = Some(XOrderState("100".zeros(10), "10".zeros(18), "10".zeros(18))),
        reserved = Some(XOrderState("100".zeros(10), "10".zeros(18), "10".zeros(18))),
        actual = Some(XOrderState("100".zeros(10), "10".zeros(18), "10".zeros(18))),
        matchable = Some(XOrderState("100".zeros(10), "10".zeros(18), "10".zeros(18)))
      )

      marketManagerActor ! XSubmitOrderReq(Some(maker1))

      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after submitted first order : " + a)
      }

      marketManagerActor ! XSubmitOrderReq(Some(taker1))

      ethereumAccessProbe.receiveN(1, 1 seconds)

      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after submitted second order: " + a)
      }

    }
  }
}
