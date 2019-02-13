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

package io.lightcone.relayer.entrypoint

import akka.util.Timeout
import io.lightcone.core.OrderStatus.{STATUS_EXPIRED, STATUS_PENDING}
import io.lightcone.relayer.support._
import io.lightcone.relayer.data._
import io.lightcone.core._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EntryPointSpec_OrderStatusMonitorExpire
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with EthereumSupport
    with MetadataManagerSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
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
          orderOpt.nonEmpty should be(true)
          orderOpt.get.getState.status should be(STATUS_PENDING)
        }
      })

      val getOrderBook = GetOrderbook.Req(
        0,
        100,
        Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      info("the sells in orderbook should be nonEmpty.")
      val orderbookRes = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) =>
          orderbook.sells.nonEmpty && orderbook.sells(0).amount == "50.00000",
        Some(Timeout(20 second))
      )
      orderbookRes.nonEmpty should be(true)
      orderbookRes.get.sells.nonEmpty should be(true)

      info(
        "the sells in orderbook should be empty, because of the orders has been expired."
      )
      val orderbookRes1 = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.isEmpty,
        Some(Timeout(15 second))
      )
      orderbookRes1.nonEmpty should be(true)
      orderbookRes1.get.sells.isEmpty should be(true)

      val assertOrderFromDbF1 = Future.sequence(orders.map { o =>
        for {
          orderOpt <- dbModule.orderService.getOrder(o.hash)
        } yield {
          orderOpt.nonEmpty should be(true)
          orderOpt.get.getState.status should be(STATUS_EXPIRED)
        }
      })

      Await.result(assertOrderFromDbF1, timeout.duration)
    }
  }

}
