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
          address = ADDRESS_RECOVERY,
          recoverBatchSize = 500,
          skipRecovery = false
        ), "accountManagerActorRecovery"
      )

      val order = XRawOrder(
        hash = "order",
        tokenS = WETH_TOKEN.address,
        tokenB = GTO_TOKEN.address,
        amountS = "50".zeros(18),
        amountB = "10000".zeros(18),
        feeParams = Some(XRawOrder.FeeParams(
          feeToken = GTO_TOKEN.address,
          feeAmount = "10".zeros(18),
          walletSplitPercentage = 100
        ))
      )

      val batchOrders1 = (0 until 500) map {
        i ⇒ order.copy(hash = "order1" + i)
      }
      val recoveryOrders1 = XRecoverOrdersRes(batchOrders1)
      actors.get(OrdersDalActor.name) ! recoveryOrders1

      val batchOrders2 = (0 until 500) map {
        i ⇒ order.copy(hash = "order2" + i)
      }
      val recoveryOrders2 = XRecoverOrdersRes(batchOrders2)
      actors.get(OrdersDalActor.name) ! recoveryOrders2

      val recoveryOrders3 = XRecoverOrdersRes(Seq.empty)
      actors.get(OrdersDalActor.name) ! recoveryOrders3

      Thread.sleep(1000)
      accountManagerRecoveryActor ! XStart()
      Thread.sleep(10000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after recovery: " + a)
      }

      //不能立即发送请求，否则可能会失败，需要等待future执行完毕
      Thread.sleep(1000)
      accountManagerRecoveryActor ! XSubmitOrderReq(Some(order.copy(hash = "order---1")))

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
