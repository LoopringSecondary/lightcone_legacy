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

import scala.concurrent.{Await, Future}

class EntryPointSpec_OrderStatusMonitor
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "market_manager",
                         | "orderbook_manager",
                         | "gas_price",
                         | "ring_settlement"]
                         |""".stripMargin)
    with OrderStatusMonitorSupport
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with EthereumQueryMockSupport
    with OrderGenerateSupport {

  //保存一批订单，等待提交
  val orders =
    (0 until 2) map { i =>
      createRawOrder(amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals))
    }

  val f = Future.sequence(orders map { o =>
    dbModule.orderService.saveOrder(o)
  })
  Await.result(f, timeout.duration)

  "start an order status monitor" must {
    "scan the order table" in {

      Thread.sleep(30000)

      val getOrderBook = XGetOrderbook(
        0,
        100,
        Some(XMarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF = singleRequest(
        getOrderBook,
        "orderbook"
      )
      val orderbookRes = Await.result(orderbookF, timeout.duration)
      orderbookRes match {
        case XOrderbook(lastPrice, sells, buys) =>
          println(s"sells:${sells}, buys:${buys}")
        case _ => assert(false)
      }
      println("#### EntryPointSpec_OrderStatusMonitor")
      Thread.sleep(100000)
    }
  }

}
