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

import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.lib.MarketHashProvider
import org.loopring.lightcone.proto._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class EntryPointSpec_SubmitSeveralOrder
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

  "send several order" must {
    "get right response in EntryPoint,DbModule,Orderbook" in {
      //下单情况
      val price1 = 10
      val price2 = 20
      val price3 = 30
      val rawOrders =
//        ((0 until 2) map { i =>
//          createRawOrder(
//            amountS = "10".zeros(18),
//            amountFee = (i + 4).toString.zeros(18)
//          )
//        }) ++
//          ((0 until 2) map { i =>
//            createRawOrder(
//              amountS = "20".zeros(18),
//              amountFee = (i + 4).toString.zeros(18)
//            )
//          }) ++
        ((0 until 2) map { i =>
          createRawOrder(
            amountS = "30".zeros(18),
            amountFee = (i + 4).toString.zeros(18)
          )
        })

      val f = Future.sequence(
        rawOrders.map { o =>
          singleRequest(XSubmitOrderReq(Some(o)), "submit_order")
        }
      )

      val res = Await.result(f, 30 second)

      println(s"res:${res}")
      Thread.sleep(1000)

      val assertOrderFromDbF = Future.sequence(
        rawOrders.map { o =>
          for {
            orderOpt <- dbModule.orderService.getOrder(o.hash)
          } yield {
            orderOpt match {
              case Some(order) =>
                assert(order.sequenceId > 0)
              case None =>
                assert(false)
            }
          }
        }
      )

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
          assert(sells.nonEmpty)
          info(s"### ${sells}")
          assert(buys.isEmpty)
        case _ => assert(false)
      }

    }
  }

}
