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
import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.core.{
  EthereumQueryActor,
  MultiAccountManagerActor
}
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EntryPointSpec_SubmitOrderThenBalanceDecrease
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

  "submit an order when the balance and allowance enough" must {
    "store it and affect depth when allowance is enough" in {

      //设置余额
      info("set the balance and allowance is enough befor submit an order")
      val f = actors.get(EthereumQueryActor.name) ? GetBalanceAndAllowancesRes(
        "",
        Map(
          "" -> XBalanceAndAllowance(
            "25".zeros(LRC_TOKEN.decimals),
            "25".zeros(LRC_TOKEN.decimals)
          )
        )
      )
      Await.result(f, timeout.duration)

      //下单情况
      val rawOrders = (0 until 1) map { i =>
        createRawOrder(
          amountS = "20".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )
      }

      val f1 = Future.sequence(rawOrders.map { o =>
        singleRequest(XSubmitOrderReq(Some(o)), "submit_order")
      })

      val res = Await.result(f1, timeout.duration)

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
              assert(order.getState.status == XOrderStatus.STATUS_PENDING)
            case None =>
              assert(false)
          }
        }
      })

      //orderbook
      Thread.sleep(1000)
      info("the depth after submit an order:")
      val getOrderBook = GetOrderbook(
        0,
        100,
        Some(XMarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF = singleRequest(getOrderBook, "orderbook")

      val orderbookRes = Await.result(orderbookF, timeout.duration)
      orderbookRes match {
        case XOrderbook(lastPrice, sells, buys) =>
          info(s"sells: ${sells}, buys:${buys}")
          assert(sells.size == 1)
          assert(
            sells(0).price == "20.000000" &&
              sells(0).amount == "20.00000" &&
              sells(0).total == "1.00000"
          )
          assert(buys.isEmpty)
        case _ => assert(false)
      }

      info("then make balance is not enough.")
      val setAllowanceF = actors.get(EthereumQueryActor.name) ? GetBalanceAndAllowancesRes(
        "",
        Map(
          "" -> XBalanceAndAllowance(
            "0".zeros(LRC_TOKEN.decimals),
            "25".zeros(LRC_TOKEN.decimals)
          )
        )
      )
      Await.result(setAllowanceF, timeout.duration)

      actors.get(MultiAccountManagerActor.name) ? XAddressBalanceUpdated(
        rawOrders(0).owner,
        LRC_TOKEN.address,
        ByteString.copyFrom("10".zeros(LRC_TOKEN.decimals).toByteArray)
      )

      Thread.sleep(1000)
      info("the depth should be empty after balance change to 10.")
      val orderbookF1 = singleRequest(getOrderBook, "orderbook")

      val orderbookRes1 = Await.result(orderbookF1, timeout.duration)
      orderbookRes1 match {
        case XOrderbook(lastPrice, sells, buys) =>
          info(s"sells: ${sells}, buys: ${buys}")
          assert(sells.isEmpty)
          assert(buys.isEmpty)
        case _ => assert(false)
      }
    }
  }

}
