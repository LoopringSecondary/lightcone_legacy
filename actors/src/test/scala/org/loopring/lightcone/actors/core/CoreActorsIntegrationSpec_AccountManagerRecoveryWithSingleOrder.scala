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

import com.typesafe.config.ConfigFactory
import akka.testkit.TestActorRef
import akka.pattern._
import org.loopring.lightcone.actors.core.CoreActorsIntegrationCommonSpec._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.persistence.OrdersDalActor
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode.{ ERR_OK, ERR_UNKNOWN }
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._

class CoreActorsIntegrationSpec_AccountManagerRecoveryWithSingleOrder
  extends CoreActorsIntegrationSpec_AccountManagerRecoverySupport(
    XMarketId(GTO_TOKEN.address, WETH_TOKEN.address),
    """
    account_manager {
      skip-recovery = no
      recover-batch-size = 1
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

  "when an accountManager starts" must {
    "first recover it and then receive order" in {

      val accountManagerRecoveryActor = TestActorRef(
        new AccountManagerActor(), "accountManagerActorRecovery"
      )
      accountManagerRecoveryActor ! XStart(ADDRESS_RECOVERY)

      val order = XRawOrder(
        hash = "order",
        tokenS = WETH_TOKEN.address,
        tokenB = GTO_TOKEN.address,
        amountS = "50".zeros(18),
        amountB = "10000".zeros(18),
        feeParams = Some(XRawOrder.FeeParams(
          tokenFee = GTO_TOKEN.address,
          amountFee = "10".zeros(18),
          walletSplitPercentage = 100
        ))
      )
      var orderHashes = (0 to 6) map ("order" + _)

      ordersDalActorProbe.expectQuery()
      ordersDalActorProbe.replyWith(Seq(
        order.copy(hash = orderHashes(0))
      ))

      Thread.sleep(1000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after first XRecoverOrdersRes: " + a)
      }

      ordersDalActorProbe.expectQuery()
      ordersDalActorProbe.replyWith(Seq(
        order.copy(hash = orderHashes(1))
      ))

      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after second XRecoverOrdersRes: " + a)
      }
      ordersDalActorProbe.expectQuery()
      ordersDalActorProbe.replyWith(Seq())

      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after last XRecoverOrdersRes: " + a)
      }

      accountManagerRecoveryActor ! XSubmitOrderReq(Some(order))

      expectMsgPF() {
        case XSubmitOrderRes(ERR_OK, Some(xorder)) ⇒
          val order: Order = xorder
          info(s"submitted an order: $order")
        case XSubmitOrderRes(ERR_UNKNOWN, None) ⇒
          info(s"occurs ERR_UNKNOWN when submitting order:$order")
      }

      Thread.sleep(1000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after submit an order: " + a)
      }

    }
  }

}
