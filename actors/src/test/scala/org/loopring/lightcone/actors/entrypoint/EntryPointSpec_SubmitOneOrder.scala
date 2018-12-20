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

import akka.actor.{Actor, ActorLogging, Props}
import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.lib.MarketHashProvider
import org.loopring.lightcone.proto._
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.{Await, ExecutionContext}

class EntryPointSpec_SubmitOneOrder
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

  "submit an order" must {
    "get right response in EntryPoint,DbModule,Orderbook" in {

      val amountS = "30"
      val amountB = "1"
      val rawOrder =
        createRawOrder(amountS = amountS.zeros(18), amountB = amountB.zeros(18))
      val f = singleRequest(
        XSubmitOrderReq(
          Some(rawOrder)
        ),
        "submit_order"
      )

      val res = Await.result(f, timeout.duration)
      res match {
        case XSubmitOrderRes(Some(order)) =>
          info(s" response ${order}")
          order.status should be(XOrderStatus.STATUS_PENDING)
        case _ => assert(false)
      }
//      val submitF = actors.get(OrderHandlerActor.name) ? XSubmitOrderReq(
//        Some(rawOrder)
//      )
//      val submitRes = Await.result(submitF, timeout.duration)
//      info(s"submit res: ${submitRes}")

      Thread.sleep(1000)
      val getOrderF = dbModule.orderService.getOrder(rawOrder.hash)

      val getOrder = Await.result(getOrderF, timeout.duration)
      getOrder match {
        case Some(order) =>
          assert(order.sequenceId > 0)
        case None => assert(false)
      }
      //orderbook
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
          assert(sells.nonEmpty)
          assert(
            sells(0).price == "10.000000" &&
//              sells(0).amount == "10.00000" &&
              sells(0).total == "1.00000"
          )
          assert(buys.isEmpty)
        case _ => assert(false)
      }

    }
  }

}
