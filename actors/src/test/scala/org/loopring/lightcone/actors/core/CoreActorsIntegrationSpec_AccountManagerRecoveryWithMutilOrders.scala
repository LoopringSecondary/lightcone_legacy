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
import org.loopring.lightcone.actors.persistence.OrdersDalActor
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode.{ ERR_OK, ERR_UNKNOWN }
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._

class CoreActorsIntegrationSpec_AccountManagerRecoveryWithMutilOrders
  extends CoreActorsIntegrationSpec_AccountManagerRecoverySupport(
    XMarketId(
      GTO_TOKEN.address,
      WETH_TOKEN.address
    )
  ) {

  "when an accountManager starts" must {
    "first recover it and then receive order" in {

      val accountManagerRecoveryActor = TestActorRef(
        new AccountManagerActor(
          actors,
          recoverBatchSize = 500,
          skipRecovery = false
        ), "accountManagerActorRecovery"
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

      val batchOrders1 = (0 until 500) map {
        i ⇒ order.copy(hash = "order1" + i)
      }
      ordersDalActorProbe.expectQuery()
      ordersDalActorProbe.replyWith(batchOrders1)

      Thread.sleep(2000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          log.debug("----orderbook status after first XRecoverOrdersRes: " + a)
      }

      val batchOrders2 = (0 until 500) map {
        i ⇒ order.copy(hash = "order2" + i)
      }
      ordersDalActorProbe.expectQuery()
      ordersDalActorProbe.replyWith(batchOrders2)

      Thread.sleep(2000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          log.debug("----orderbook status after second XRecoverOrdersRes: " + a)
      }
      ordersDalActorProbe.expectQuery()
      ordersDalActorProbe.replyWith(Seq())

      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          log.debug("----orderbook status after last XRecoverOrdersRes: " + a)
      }

      //不能立即发送请求，否则可能会失败，需要等待future执行完毕
      Thread.sleep(1000)
      accountManagerRecoveryActor ! XSubmitOrderReq(Some(order.copy(hash = "order---1")))

      expectMsgPF() {
        case XSubmitOrderRes(ERR_OK, Some(xorder)) ⇒
          val order: Order = xorder
          log.debug(s"submitted an order: $order")
        case XSubmitOrderRes(ERR_UNKNOWN, None) ⇒
          log.debug(s"occurs ERR_UNKNOWN when submitting order:$order")
      }

      Thread.sleep(1000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          log.debug("----orderbook status after submit an order: " + a)
      }

    }
  }

}
