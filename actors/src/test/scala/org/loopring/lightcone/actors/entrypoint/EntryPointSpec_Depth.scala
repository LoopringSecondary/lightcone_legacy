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

class EntryPointSpec_Depth
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
    with MarketManagerSupport
    with OrderbookManagerSupport
    with EthereumQueryMockSupport
    with OrderGenerateSupport {

  "submit several orders" must {
    "get the right depth" in {
      val rawOrder1 =
        createRawOrder(amountS = "123456789".zeros(10), amountB = "1".zeros(18))
      val f1 = singleRequest(
        XSubmitOrderReq(
          Some(rawOrder1)
        ),
        "submit_order"
      )

      val res1 = Await.result(f1, timeout.duration)
      res1 match {
        case XSubmitOrderRes(Some(order)) =>
          info(s" response ${order}")
          order.status should be(XOrderStatus.STATUS_PENDING)
        case _ => assert(false)
      }

      val rawOrder2 =
        createRawOrder(amountS = "223456789".zeros(10), amountB = "1".zeros(18))
      val f2 = singleRequest(
        XSubmitOrderReq(
          Some(rawOrder2)
        ),
        "submit_order"
      )

      val res2 = Await.result(f2, timeout.duration)
      res2 match {
        case XSubmitOrderRes(Some(order)) =>
          info(s" response ${order}")
          order.status should be(XOrderStatus.STATUS_PENDING)
        case _ => assert(false)
      }

      val rawOrder3 =
        createRawOrder(amountS = "323456789".zeros(10), amountB = "1".zeros(18))
      val f3 = singleRequest(
        XSubmitOrderReq(
          Some(rawOrder3)
        ),
        "submit_order"
      )

      val res3 = Await.result(f3, timeout.duration)
      res3 match {
        case XSubmitOrderRes(Some(order)) =>
          info(s" response ${order}")
          order.status should be(XOrderStatus.STATUS_PENDING)
        case _ => assert(false)
      }

      val rawOrder4 =
        createRawOrder(amountS = "323456689".zeros(10), amountB = "1".zeros(18))
      val f4 = singleRequest(
        XSubmitOrderReq(
          Some(rawOrder4)
        ),
        "submit_order"
      )

      val res4 = Await.result(f4, timeout.duration)
      res4 match {
        case XSubmitOrderRes(Some(order)) =>
          info(s" response ${order}")
          order.status should be(XOrderStatus.STATUS_PENDING)
        case _ => assert(false)
      }

      Thread.sleep(3000)
      //根据不同的level需要有不同的汇总
      val getOrderBook1 = XGetOrderbook(
        0,
        100,
        Some(XMarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF1 = singleRequest(
        getOrderBook1,
        "orderbook"
      )
      val orderbookRes1 = Await.result(orderbookF1, timeout.duration)
      orderbookRes1 match {
        case XOrderbook(lastPrice, sells, buys) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.size == 4)
          assert(
            sells(0).price == "1.234568" &&
              sells(0).amount == "1.23457" &&
              sells(0).total == "1.00000"
          )
          assert(
            sells(1).price == "2.234568" &&
              sells(1).amount == "2.23457" &&
              sells(1).total == "1.00000"
          )
        case _ => assert(false)
      }

      //下一level
      val getOrderBook2 = XGetOrderbook(
        1,
        100,
        Some(XMarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF2 = singleRequest(
        getOrderBook2,
        "orderbook"
      )
      val orderbookRes2 = Await.result(orderbookF2, timeout.duration)
      orderbookRes2 match {
        case XOrderbook(lastPrice, sells, buys) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.size == 3)
          assert(
            sells(0).price == "1.23457" &&
              sells(0).amount == "1.23457" &&
              sells(0).total == "1.00000"
          )
          assert(
            sells(2).price == "3.23457" &&
              sells(2).amount == "6.46913" &&
              sells(2).total == "2.00000"
          )
        case _ => assert(false)
      }

    }
  }

}
