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

import akka.testkit.TestActorRef
import org.loopring.lightcone.actors.core.CoreActorsIntegrationCommonSpec._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode.{ ERR_OK, ERR_UNKNOWN }
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.deployment.XStart

class CoreActorsIntegrationSpec_AccountManagerRecoveryWithSingleOrder
  extends CoreActorsIntegrationSpec_AccountManagerRecoverySupport(XMarketId(GTO_TOKEN.address, WETH_TOKEN.address)) {

  "when an accountManager starts" must {
    "first recover it and then receive order" in {
      val accountManagerRecoveryActor = TestActorRef(
        new AccountManagerActor(
          actors,
          address = ADDRESS_RECOVERY,
          recoverBatchSize = 1,
          skipRecovery = false
        ), "accountManagerActor3"
      )
      accountManagerRecoveryActor ! XStart

      val order = XRawOrder(
        hash = "order",
        tokenS = WETH_TOKEN.address,
        tokenB = GTO_TOKEN.address,
        feeToken = GTO_TOKEN.address,
        amountS = "50".zeros(18),
        amountB = "10000".zeros(18),
        feeAmount = "10".zeros(18),
        walletSplitPercentage = 100
      )
      var orderIds = (0 to 6) map ("order" + _)

      Thread.sleep(500)
      orderDatabaseAccessProbe.expectQuery()
      orderDatabaseAccessProbe.replyWith(Seq(
        order.copy(hash = orderIds(0))
      ))

      Thread.sleep(1000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after first XRecoverOrdersRes: " + a)
      }

      orderDatabaseAccessProbe.expectQuery()
      orderDatabaseAccessProbe.replyWith(Seq(
        order.copy(hash = orderIds(1))
      ))

      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after second XRecoverOrdersRes: " + a)
      }
      orderDatabaseAccessProbe.expectQuery()
      orderDatabaseAccessProbe.replyWith(Seq())

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
