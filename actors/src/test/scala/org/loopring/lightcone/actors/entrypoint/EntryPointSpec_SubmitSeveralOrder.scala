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

  "submit several order then cancel it" must {
    "get right response in EntryPoint,DbModule,Orderbook" in {
      //下单情况
      //初始化getTokenManager，否则会有同步问题
      //      val order1 =
      //        createRawOrder(amountS = "10".zeros(18), amountFee = "1".zeros(15))
      //      val f = singleRequest(XSubmitOrderReq(Some(order1)), "submit_order")
      //      Await.result(f, 3 second)

      val marketId = XMarketId(LRC_TOKEN.address, WETH_TOKEN.address)

      val rawOrders =
        ((0 until 1) map { i =>
          createRawOrder(amountS = "10".zeros(18), amountFee = "1".zeros(18))
        }) ++
          ((0 until 2) map { i =>
            createRawOrder(amountS = "20".zeros(18), amountFee = "2".zeros(18))
          }) ++
          ((0 until 2) map { i =>
            createRawOrder(amountS = "30".zeros(18), amountFee = "3".zeros(18))
          })

      val f1 = Future.sequence(rawOrders.map { o =>
        singleRequest(XSubmitOrderReq(Some(o)), "submit_order")
      })

      Await.result(f1, 3 second)

      val assertOrderFromDbF = Future.sequence(rawOrders.map { o =>
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
      })

      //orderbook
      val getOrderBook = XGetOrderbook(0, 100, Some(marketId))
      val orderbookF = singleRequest(getOrderBook, "orderbook")

      val orderbookRes = Await.result(orderbookF, timeout.duration)
      orderbookRes match {
        case XOrderbook(_, sells, buys) =>
          info(s"sells: ${sells}")
          assert(sells.size == 3)
          assert(
            sells(0).price == "10.000000" &&
              sells(0).amount == "10.00000" &&
              sells(0).total == "1.00000"
          )
          assert(
            sells(1).price == "20.000000" &&
              sells(1).amount == "40.00000" &&
              sells(1).total == "2.00000"
          )
          assert(
            sells(2).price == "30.000000" &&
              sells(2).amount == "60.00000" &&
              sells(2).total == "2.00000"
          )

          assert(buys.isEmpty)
        case _ => assert(false)
      }

      info("then cancel one of it.")
      val cancelReq = XCancelOrderReq(
        rawOrders(1).hash,
        rawOrders(1).owner,
        XOrderStatus.STATUS_CANCELLED_BY_USER,
        Some(marketId)
      )

      val cancelF = singleRequest(cancelReq, "cancel_order")
      Await.result(cancelF, timeout.duration)

      val orderbookF1 = singleRequest(getOrderBook, "orderbook")

      val res = Await.result(orderbookF1, timeout.duration)
      println("====>>>" + res)
      res match {
        case XOrderbook(_, sells, buys) =>
          assert(sells.size == 2)
          assert(
            sells(0).price == "20.000000" &&
              sells(0).amount == "40.00000" &&
              sells(0).total == "2.00000"
          )
          assert(
            sells(1).price == "30.000000" &&
              sells(1).amount == "60.00000" &&
              sells(1).total == "2.00000"
          )
          assert(buys.isEmpty)
        case _ => assert(false)
      }
    }
  }

}
