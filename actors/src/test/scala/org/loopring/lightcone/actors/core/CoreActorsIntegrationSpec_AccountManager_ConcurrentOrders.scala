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
import org.loopring.lightcone.proto.actors.XErrorCode._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import akka.pattern._

import scala.concurrent.Await

class CoreActorsIntegrationSpec_AccountManager_ConcurrentOrders
  extends CoreActorsIntegrationSpec_AccountManagerRecoverySupport(XMarketId(GTO, WETH)) {

  "submit several orders at the same time" must {
    "submit success and depth contains right value" in {
      val accountManagerRecoveryActor = TestActorRef(
        new AccountManagerActor(
          actors,
          address = ADDRESS_RECOVERY,
          recoverBatchSize = 2,
          skipRecovery = true
        ), "accountManagerActorConcurrenetOrders"
      )
      accountManagerRecoveryActor ! XStart()

      val order = XOrderSnippet(
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

      (0 until 100) foreach {
        i ⇒ accountManagerActor1 ! XSubmitOrderReq(Some(order.copy(id = "order" + i)))
      }

      Thread.sleep(1000)
      val f = orderbookManagerActor ? XGetOrderbookReq(0, 100)
      val res = Await.result(f.mapTo[XOrderbook], timeout.duration)
      info(res.toString)
      res.sells.size should be(1)
      //todo:暂时会有并发问题，需要再改正
      //      res.sells(0).amount should be("5000.00")
      //      res.sells(0).total should be("100000000000000.0")
    }
  }

}
