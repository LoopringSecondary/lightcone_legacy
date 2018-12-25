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
        singleRequest(XSubmitOrderReq(Some(o)), "submit_order")
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
              assert(order.getState.status == XOrderStatus.STATUS_PENDING)
            case None =>
              assert(false)
          }
        }

      })

      //orderbook
      Thread.sleep(1000)
      val getOrderBook = XGetOrderbook(
        0,
        100,
        Some(XMarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF = singleRequest(getOrderBook, "orderbook")

      val orderbookRes = Await.result(orderbookF, timeout.duration)
      orderbookRes match {
        case XOrderbook(_, sells, buys) =>
          info(s"sells: ${sells}")
          assert(sells.size == 3)
          assert(
            sells(0).price == "10.000000" &&
              sells(0).amount == "20.00000" &&
              sells(0).total == "2.00000"
          )
          assert(
            sells(1).price == "20.000000" &&
              sells(1).amount == "40.00000" &&
              sells(1).total == "2.00000"
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

      info("then cancel one of it, the depth should be changed.")
      val cancelReq = XCancelOrderReq(
        rawOrders(0).hash,
        rawOrders(0).owner,
        XOrderStatus.STATUS_CANCELLED_BY_USER,
        Some(XMarketId(rawOrders(0).tokenS, rawOrders(0).tokenB))
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
                  order.getState.status == XOrderStatus.STATUS_CANCELLED_BY_USER
                )
              } else {
                assert(order.getState.status == XOrderStatus.STATUS_PENDING)
              }
            case None =>
              assert(false)
          }
        }
      })

      Thread.sleep(1000)
      val orderbookF1 = singleRequest(getOrderBook, "orderbook")

      val cancelF = singleRequest(cancelReq, "cancel_order")
      Await.result(cancelF, timeout.duration)

      /*
    ERROR
          org.loopring.lightcone.lib.ErrorException: ErrorException(
          ERR_INTERNAL_UNKNOWN: msg:JsonRpcError(4002,
          Some(failed to submit order:
          XRawOrder(0x3518ce27b5f6ff3c8dfb8f0d3f4c58fb09294c7c8d4678a4d6a433faa7de46d6,
          1,0xb7e0dae0a3e4e146bcaf0fe782be5afb14041a10,
          0xa345b6c2e5ce5970d026cea8591dc28958ff6edc,
          0x08d24fc29cdccf8e9ca45eef05384c58f8a8e94f,
          <ByteString@3eee611 size=9>,<ByteString@1c4324b9 size=8>,
          1545606389,Some(Params(,,,,1545616389,,,false,ERC20,ERC20,ERC20,)),
          Some(FeeParams(0xa345b6c2e5ce5970d026cea8591dc28958ff6edc,
          <ByteString@613cd30 size=8>,0,0,0,,0)),None,
          Some(State(1545596389381,1545596389381,0,0,STATUS_NEW,
          <ByteString@3a80d779 size=0>,<ByteString@3a80d779 size=0>,
          <ByteString@3a80d779 size=0>,<ByteString@3a80d779 size=0>,
          <ByteString@3a80d779 size=0>,<ByteString@3a80d779 size=0>))
          ,0,)),None))
       */
      res match {
        case XOrderbook(_, sells, buys) =>
          assert(sells.size == 2)
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
