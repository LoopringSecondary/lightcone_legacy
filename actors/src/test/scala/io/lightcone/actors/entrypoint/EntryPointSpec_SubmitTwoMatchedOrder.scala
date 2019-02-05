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

package io.lightcone.actors.entrypoint

import io.lightcone.actors.support._
import io.lightcone.proto._
import io.lightcone.core._

import scala.concurrent.Await

class EntryPointSpec_SubmitTwoMatchedOrder
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

  "submit two fullMatched orders" must {
    "submit a ring and affect to orderbook before blocked and executed in eth" in {
      val order1 =
        createRawOrder(
          amountS = "10".zeros(LRC_TOKEN.decimals),
          tokenS = LRC_TOKEN.address,
          amountB = "1".zeros(WETH_TOKEN.decimals),
          tokenB = WETH_TOKEN.address,
          amountFee = "10".zeros(LRC_TOKEN.decimals),
          tokenFee = LRC_TOKEN.address
        )

      Await.result(
        singleRequest(SubmitOrder.Req(Some(order1)), "submit_order"),
        timeout.duration
      )

      info("getOrderbook after submit one order with market: LRC-WETH")
      val orderbookRes3 = expectOrderbookRes(
        GetOrderbook
          .Req(0, 100, Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))),
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      orderbookRes3 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"price:${lastPrice}, sells:${sells}, buys:${buys}")
          assert(lastPrice == 0.0)
          assert(sells.size == 1)
          assert(
            sells(0).price == "0.100000" &&
              sells(0).amount == "10.00000" &&
              sells(0).total == "1.00000"
          )
          assert(buys.isEmpty)
        case _ => assert(false)
      }

      // -----------------------
      val order2 =
        createRawOrder(
          amountS = "1".zeros(WETH_TOKEN.decimals) / 2,
          tokenS = WETH_TOKEN.address,
          amountB = "10".zeros(LRC_TOKEN.decimals) / 2,
          tokenB = LRC_TOKEN.address,
          amountFee = "10".zeros(LRC_TOKEN.decimals),
          tokenFee = LRC_TOKEN.address
        )

      Await.result(
        singleRequest(SubmitOrder.Req(Some(order2)), "submit_order"),
        timeout.duration
      )

      info(
        "get orderbook after submit the sencond order with market: WETH-LRC and half amount of the first order."
      )
      val orderbookRes = expectOrderbookRes(
        GetOrderbook
          .Req(0, 100, Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))),
        (orderbook: Orderbook) =>
          orderbook.sells.nonEmpty && orderbook.latestPrice == 0.1
      )
      orderbookRes match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"price:${lastPrice}, sells:${sells}, buys:${buys}")
          assert(sells.size == 1)
          assert(lastPrice == 0.1)
          assert(
            sells(0).price == "0.100000" &&
              sells(0).amount == "5.00000" &&
              sells(0).total == "0.50000"
          )
          assert(buys.isEmpty)
        case _ => assert(false)
      }

      // -----------------------
      val order3 =
        createRawOrder(
          amountS = "1".zeros(WETH_TOKEN.decimals) / 2,
          tokenS = WETH_TOKEN.address,
          amountB = "10".zeros(LRC_TOKEN.decimals) / 2,
          tokenB = LRC_TOKEN.address,
          amountFee = "11".zeros(LRC_TOKEN.decimals),
          tokenFee = LRC_TOKEN.address
        )

      Await.result(
        singleRequest(SubmitOrder.Req(Some(order3)), "submit_order"),
        timeout.duration
      )

      info("submit a order like order2, then the orderbook should be empty.")
      val orderbookRes1 = expectOrderbookRes(
        GetOrderbook
          .Req(0, 100, Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))),
        (orderbook: Orderbook) =>
          orderbook.sells.isEmpty && orderbook.buys.isEmpty
      )
      orderbookRes1 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(buys.isEmpty && sells.isEmpty)
        case _ => assert(false)
      }

      // -----------------------
      val order4 =
        createRawOrder(
          amountS = "1".zeros(WETH_TOKEN.decimals) / 2,
          tokenS = WETH_TOKEN.address,
          amountB = "10".zeros(LRC_TOKEN.decimals) / 2,
          tokenB = LRC_TOKEN.address,
          amountFee = "14".zeros(LRC_TOKEN.decimals),
          tokenFee = LRC_TOKEN.address
        )

      Await.result(
        singleRequest(SubmitOrder.Req(Some(order4)), "submit_order"),
        timeout.duration
      )

      info("submit one more order and the orderbook should be nonEmpty")

      val orderbookRes2 = expectOrderbookRes(
        GetOrderbook
          .Req(0, 100, Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))),
        (orderbook: Orderbook) => orderbook.buys.nonEmpty
      )
      info(s"orderbookRes2 ${orderbookRes2}")
      orderbookRes2 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(buys.size == 1)
          assert(
            buys(0).price == "0.100000" &&
              buys(0).amount == "5.00000" &&
              buys(0).total == "0.50000"
          )
          assert(sells.isEmpty)
        case _ => assert(false)
      }

    }
  }

}
