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

import akka.pattern._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._

import scala.concurrent.Await

class CoreActorsIntegrationSpec_SubmitOneOrder
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "order_handler",
                         | "multi_account_manager",
                         | "market_manager",
                         | "orderbook_manager",
                         | "gas_price"]
                         |""".stripMargin)
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with EthereumQueryMockSupport
    with OrderGenerateSupport {

  "submiting one from OrderHandleActor" must {
    "get depth in OrderbookManagerActor" in {
      val rawOrder = createRawOrder()

      val submitF = actors.get(OrderHandlerActor.name) ? XSubmitOrderReq(
        Some(rawOrder)
      )
      val submitRes = Await.result(submitF, timeout.duration)
      info(s"submit res: ${submitRes}")

      Thread.sleep(1000)
      actors.get(OrderbookManagerActor.name) ! XGetOrderbook(
        0,
        100,
        Some(XMarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )

      expectMsgPF() {
        case a: XOrderbook =>
          info("----orderbook status after submitted an order: " + a)
          a.sells should not be empty
      }

    }

  }

}
