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

import io.lightcone.relayer.support._
import io.lightcone.core._
import io.lightcone.relayer.data._
import scala.concurrent.{Await, Future}

class EntryPointSpec_SubmitSeveralOrder
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with EthereumSupport
    with MetadataManagerSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderGenerateSupport {

  "submit several order then cancel it" must {
    "get right response in EntryPoint,DbModule,Orderbook" in {
      //下单情况
      val rawOrders =
        ((0 until 2) map { i =>
          createRawOrder(
            amountS = "10".zeros(LRC_TOKEN.decimals),
            amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
          )
        }) ++
          ((0 until 2) map { i =>
            createRawOrder(
              amountS = "20".zeros(LRC_TOKEN.decimals),
              amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
            )
          }) ++
          ((0 until 2) map { i =>
            createRawOrder(
              amountS = "30".zeros(LRC_TOKEN.decimals),
              amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
            )
          })

      val f1 = Future.sequence(rawOrders.map { o =>
        singleRequest(SubmitOrder.Req(Some(o)), "submit_order")
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
              assert(order.getState.status == OrderStatus.STATUS_PENDING)
            case None =>
              assert(false)
          }
        }
      })

      //orderbook
      val getOrderBook = GetOrderbook.Req(
        0,
        100,
        Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))
      )

      val orderbookRes = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) =>
          orderbook.sells.nonEmpty &&
            orderbook.sells.size == 3 &&
            orderbook.sells(0).total == "2.00000" &&
            orderbook.sells(1).total == "2.00000" &&
            orderbook.sells(2).total == "2.00000"
      )
      orderbookRes match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.size == 3)

          assert(
            sells(0).price == "0.033334" &&
              sells(0).amount == "60.00000" &&
              sells(0).total == "2.00000"
          )

          assert(
            sells(1).price == "0.050000" &&
              sells(1).amount == "40.00000" &&
              sells(1).total == "2.00000"
          )

          assert(
            sells(2).price == "0.100000" &&
              sells(2).amount == "20.00000" &&
              sells(2).total == "2.00000"
          )

          assert(buys.isEmpty)
        case _ => assert(false)
      }

      info("then cancel one of it, the depth should be changed.")
      val cancelReq = CancelOrder.Req(
        rawOrders(0).hash,
        rawOrders(0).owner,
        OrderStatus.STATUS_SOFT_CANCELLED_BY_USER,
        Some(MarketPair(rawOrders(0).tokenS, rawOrders(0).tokenB)),
        rawOrders(0).getParams.sig
      )

      val cancelF = singleRequest(cancelReq, "cancel_order")
      Await.result(cancelF, timeout.duration)

      info(
        "the first order's status in db should be STATUS_SOFT_CANCELLED_BY_USER"
      )
      val assertOrderFromDbF2 = Future.sequence(rawOrders.map { o =>
        for {
          orderOpt <- dbModule.orderService.getOrder(o.hash)
        } yield {
          orderOpt match {
            case Some(order) =>
              if (order.hash == rawOrders(0).hash) {
                assert(
                  order.getState.status == OrderStatus.STATUS_SOFT_CANCELLED_BY_USER
                )
              } else {
                assert(order.getState.status == OrderStatus.STATUS_PENDING)
              }
            case None =>
              assert(false)
          }
        }
      })

      val orderbookRes1 = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) =>
          orderbook.sells.nonEmpty && orderbook.sells(0).total == "2.00000"
      )
      orderbookRes1 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.size == 3)
          assert(
            sells(0).price == "0.033334" &&
              sells(0).amount == "60.00000" &&
              sells(0).total == "2.00000"
          )

          assert(
            sells(1).price == "0.050000" &&
              sells(1).amount == "40.00000" &&
              sells(1).total == "2.00000"
          )

          assert(
            sells(2).price == "0.100000" &&
              sells(2).amount == "10.00000" &&
              sells(2).total == "1.00000"
          )

          assert(buys.isEmpty)
        case _ => assert(false)
      }
    }
  }

}
