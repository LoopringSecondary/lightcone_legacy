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

package org.loopring.lightcone.actors.entrypoint

import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._

import scala.concurrent.Await
import scala.concurrent.duration._

class EntryPointSpec_SubmitTwoMatchedOrder
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "order_handler",
                         | "multi_account_manager",
                         | "market_manager",
                         | "orderbook_manager",
                         | "gas_price",
                         | "ring_settlement"]
                         |""".stripMargin)
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with EthereumQueryMockSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderGenerateSupport {

  "submit two fullMatched orders" must {
    "submit a ring and affect to orderbook before blocked and executed in eth" in {
      val order1 =
        createRawOrder(
          amountS = "10".zeros(18),
          tokenS = LRC_TOKEN.address,
          amountB = "1".zeros(18),
          tokenB = WETH_TOKEN.address,
          amountFee = "10".zeros(18),
          tokenFee = LRC_TOKEN.address
        )
      val f = singleRequest(SubmitOrder.Req(Some(order1)), "submit_order")
      Await.result(f, timeout.duration)

      val order2 =
        createRawOrder(
          amountS = "5".zeros(17),
          tokenS = WETH_TOKEN.address,
          amountB = "5".zeros(18),
          tokenB = LRC_TOKEN.address,
          amountFee = "10".zeros(18),
          tokenFee = LRC_TOKEN.address
        )
      val f1 = singleRequest(SubmitOrder.Req(Some(order2)), "submit_order")
      Await.result(f1, timeout.duration)

      Thread.sleep(1000)
      val getOrderBook = GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF2 = singleRequest(getOrderBook, "orderbook")
      val orderbookRes2 = Await.result(orderbookF2, timeout.duration)
      orderbookRes2 match {
        case GetOrderbook.Res(Some(Orderbook(lastPrice, sells, buys))) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.isEmpty && buys.size == 1)
          assert(
            buys(0).total == "0.50000" &&
              buys(0).price == "10.000000" &&
              buys(0).amount == "5.00000"
          )
        case _ => assert(false)
      }

      val getOrderBook1 = GetOrderbook.Req(
        1,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF1 = singleRequest(getOrderBook1, "orderbook")
      val orderbookRes1 = Await.result(orderbookF1, timeout.duration)
      orderbookRes1 match {
        case GetOrderbook.Res(Some(Orderbook(lastPrice, sells, buys))) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.isEmpty && buys.size == 1)
          assert(
            buys(0).total == "0.50000" &&
              buys(0).price == "10.00000" &&
              buys(0).amount == "5.00000"
          )
        case _ => assert(false)
      }
    }
  }

}
