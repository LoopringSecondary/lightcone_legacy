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

import akka.pattern._
import org.loopring.lightcone.actors.core.EthereumQueryActor
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.data._
import scala.concurrent.{Await, Future}

class EntryPointSpec_SubmitOrderInBalanceNotEnoughCase
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

  "submit several order when the balance is not enough" must {
    "the fisrt should be submit success and the second should be failed" in {

      //设置余额
      //todo: allowance 为0 的逻辑是什么，accountmanager与marketmanager中是否需要保存
      val f = actors.get(EthereumQueryActor.name) ? GetBalanceAndAllowancesRes(
        "",
        Map(
          "" -> BalanceAndAllowance(
            "30".zeros(LRC_TOKEN.decimals),
            "30".zeros(LRC_TOKEN.decimals)
          )
        )
      )
      Await.result(f, timeout.duration)

      //下单情况
      val rawOrders = (0 until 2) map { i =>
        createRawOrder(
          amountS = "20".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )
      }

      val f1 = Future.sequence(rawOrders.map { o =>
        singleRequest(SubmitOrderReq(Some(o)), "submit_order").recover {
          case e: Throwable =>
            info(
              s"submit the second order shouldn't be success. it will occurs err: ${e} when submit order:${o}"
            )
            Future.successful(Unit)
        }
      })

      val res = Await.result(f1, timeout.duration)

      info(
        "the first order's sequenceId in db should > 0 and status should be STATUS_PENDING and STATUS_CANCELLED_LOW_BALANCE "
      )
      val assertOrderFromDbF = Future.sequence(rawOrders.map { o =>
        for {
          orderOpt <- dbModule.orderService.getOrder(o.hash)
        } yield {
          orderOpt match {
            case Some(order) =>
              assert(order.sequenceId > 0)
              if (order.id == rawOrders(0).hash) {
                assert(order.getState.status == OrderStatus.STATUS_PENDING)
              } else {
                assert(
                  order.getState.status == OrderStatus.STATUS_CANCELLED_LOW_BALANCE
                )
              }
            case None =>
              assert(false)
          }
        }
      })

      //orderbook
      Thread.sleep(1000)
      val getOrderBook = GetOrderbook(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF = singleRequest(getOrderBook, "orderbook")

      val orderbookRes = Await.result(orderbookF, timeout.duration)
      orderbookRes match {
        case XOrderbook(lastPrice, sells, buys) =>
          info(s"sells: ${sells}， buys: ${buys}")
          assert(sells.size == 1)
          assert(
            sells(0).price == "20.000000" &&
              sells(0).amount == "20.00000" &&
              sells(0).total == "1.00000"
          )
          assert(buys.isEmpty)
        case _ => assert(false)
      }

      info("then cancel the first one, the depth should be changed to empy.")
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
                assert(
                  order.getState.status == OrderStatus.STATUS_CANCELLED_LOW_BALANCE
                )
              }
            case None =>
              assert(false)
          }
        }
      })

      Thread.sleep(1000)
      info(
        "the result of orderbook should be empty after cancel the first order."
      )
      val orderbookF1 = singleRequest(getOrderBook, "orderbook")

      val orderbookRes1 = Await.result(orderbookF1, timeout.duration)
      orderbookRes1 match {
        case XOrderbook(lastPrice, sells, buys) =>
          assert(sells.isEmpty)
          assert(buys.isEmpty)
        case _ => assert(false)
      }
    }
  }

}
