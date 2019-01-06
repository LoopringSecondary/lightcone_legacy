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

import akka.util.Timeout
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EntryPointSpec_OrderStatusMonitorExpire
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
    with OrderGenerateSupport
    with OrderStatusMonitorSupport {

  "start an order status monitor" must {
    "scan the order table and submit the canceled order to AccountManager" in {

      //提交一批订单
      val orders =
        (0 until 5) map { i =>
          createRawOrder(
            amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals),
            validUntil = timeProvider.getTimeSeconds().toInt + 10
          )
        }

      val f = Future.sequence(orders.map { o =>
        singleRequest(SubmitOrder.Req(Some(o)), "submit_order")
      })

      Await.result(f, timeout.duration)
      info("confirm the status of the orders in db should be STATUS_PENDING")
      val assertOrderFromDbF = Future.sequence(orders.map { o =>
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

      val getOrderBook = GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      info("the sells in orderbook should be nonEmpty.")
      val orderbookRes = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) =>
          orderbook.sells.nonEmpty && orderbook.sells(0).amount == "50.00000",
        Some(Timeout(20 second))
      )
      orderbookRes match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.nonEmpty)
        case _ => assert(false)
      }

      info(
        "the sells in orderbook should be empty, because of the orders has been expired."
      )
      val orderbookRes1 = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.isEmpty,
        Some(Timeout(15 second))
      )
      orderbookRes1 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.isEmpty)
        case _ => assert(false)
      }

      val assertOrderFromDbF1 = Future.sequence(orders.map { o =>
        for {
          orderOpt <- dbModule.orderService.getOrder(o.hash)
        } yield {
          orderOpt match {
            case Some(order) =>
              assert(order.sequenceId > 0)
              assert(order.getState.status == OrderStatus.STATUS_EXPIRED)
            case None =>
              assert(false)
          }
        }
      })

      Await.result(assertOrderFromDbF1, timeout.duration)
    }
  }

}
