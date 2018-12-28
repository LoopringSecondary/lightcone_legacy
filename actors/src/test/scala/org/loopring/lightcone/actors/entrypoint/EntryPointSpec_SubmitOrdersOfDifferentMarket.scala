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

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EntryPointSpec_SubmitOrdersOfDifferentMarket
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

  "submit several orders of different markets" must {
    "use the right shardId" in {
      //下单情况
      val rawOrders =
        //lrc-weth
        ((0 until 2) map { i =>
          createRawOrder(
            amountS = "10".zeros(LRC_TOKEN.decimals),
            amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
          )
        }) ++
          //gto-weth
          ((0 until 2) map { i =>
            createRawOrder(
              amountS = "20".zeros(GTO_TOKEN.decimals),
              tokenS = GTO_TOKEN.address,
              amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
            )
          }) ++
          //lrc-weth
          ((0 until 2) map { i =>
            createRawOrder(
              amountS = "30".zeros(LRC_TOKEN.decimals),
              amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
            )
          })

      val f1 = Future.sequence(rawOrders.map { o =>
        singleRequest(SubmitOrderReq(Some(o)), "submit_order")
      })

      val res = Await.result(f1, 3 second)

      info(
        "the first order's sequenceId in db should > 0 and status should be STATUS_PENDING"
      )
      val assertOrderFromDbF = Future.sequence(rawOrders.map { o =>
        for {
          orderOpt <- dbModule.orderService.getOrder(o.hash)
        } yield {
          orderOpt match {
            case Some(order) =>
              assert(order.sequenceId > 0)
              assert(order.getState.status == OrderStatus.STATUS_PENDING)
            case None =>
              assert(false)
          }
        }
      })

      //orderbook
      Thread.sleep(1000)
      info("then test the orderbook of LRC-WETH")
      val orderbookLrcF = singleRequest(
        GetOrderbook(
          0,
          100,
          Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
        ),
        "orderbook"
      )

      val orderbookLrcRes = Await.result(orderbookLrcF, timeout.duration)
      orderbookLrcRes match {
        case Orderbook(lastPrice, sells, buys) =>
          info(s"sells: ${sells}")
          assert(sells.size == 2)
          assert(
            sells(0).price == "10.000000" &&
              sells(0).amount == "20.00000" &&
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

      info("then test the orderbook of GTO-WETH")
      val orderbookGtoF = singleRequest(
        GetOrderbook(
          0,
          100,
          Some(MarketId(GTO_TOKEN.address, WETH_TOKEN.address))
        ),
        "orderbook"
      )

      val orderbookGtoRes = Await.result(orderbookGtoF, timeout.duration)
      orderbookGtoRes match {
        case Orderbook(lastPrice, sells, buys) =>
          info(s"sells: ${sells}")
          assert(sells.size == 1)
          assert(
            sells(0).price == "20.000000" &&
              sells(0).amount == "40.00000" &&
              sells(0).total == "2.00000"
          )
          assert(buys.isEmpty)
        case _ => assert(false)
      }

      info("then cancel one of it, the depth should be changed.")
      val cancelReq = CancelOrderReq(
        rawOrders(0).hash,
        rawOrders(0).owner,
        OrderStatus.STATUS_CANCELLED_BY_USER,
        Some(MarketId(rawOrders(0).tokenS, rawOrders(0).tokenB))
      )

      val cancelF = singleRequest(cancelReq, "cancel_order")
      Await.result(cancelF, timeout.duration)

      info("the first order's status in db should be STATUS_CANCELLED_BY_USER")
      val assertOrderFromDbF2 = Future.sequence(rawOrders.map { o =>
        for {
          orderOpt <- dbModule.orderService.getOrder(o.hash)
        } yield {
          orderOpt match {
            case Some(order) =>
              if (order.hash == rawOrders(0).hash) {
                assert(
                  order.getState.status == OrderStatus.STATUS_CANCELLED_BY_USER
                )
              } else {
                assert(order.getState.status == OrderStatus.STATUS_PENDING)
              }
            case None =>
              assert(false)
          }
        }
      })

      Thread.sleep(1000)
      val orderbookF1 = singleRequest(
        GetOrderbook(
          0,
          100,
          Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
        ),
        "orderbook"
      )

      val orderbookRes1 = Await.result(orderbookF1, timeout.duration)
      orderbookRes1 match {
        case Orderbook(lastPrice, sells, buys) =>
          assert(sells.size == 2)
          assert(
            sells(0).price == "10.000000" &&
              sells(0).amount == "10.00000" &&
              sells(0).total == "1.00000"
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
