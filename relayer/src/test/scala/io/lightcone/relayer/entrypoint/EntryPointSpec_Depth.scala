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
import io.lightcone.relayer.data._
import io.lightcone.core._

import scala.concurrent.Await

class EntryPointSpec_Depth
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

  "submit several orders" must {
    "get the right depth" in {
      val rawOrder1 =
        createRawOrder(amountS = "123456789".zeros(10), amountB = "1".zeros(18))
      val f1 = singleRequest(SubmitOrder.Req(Some(rawOrder1)), "submit_order")

      val res1 = Await.result(f1, timeout.duration)
      res1 match {
        case SubmitOrder.Res(Some(order)) =>
          info(s" response ${order}")
          order.status should be(OrderStatus.STATUS_PENDING)
        case _ => assert(false)
      }

      val rawOrder2 =
        createRawOrder(amountS = "223456789".zeros(10), amountB = "1".zeros(18))
      val f2 = singleRequest(SubmitOrder.Req(Some(rawOrder2)), "submit_order")

      val res2 = Await.result(f2, timeout.duration)
      res2 match {
        case SubmitOrder.Res(Some(order)) =>
          info(s" response ${order}")
          order.status should be(OrderStatus.STATUS_PENDING)
        case _ => assert(false)
      }

      val rawOrder3 =
        createRawOrder(amountS = "323456789".zeros(10), amountB = "1".zeros(18))
      val f3 = singleRequest(SubmitOrder.Req(Some(rawOrder3)), "submit_order")

      val res3 = Await.result(f3, timeout.duration)
      res3 match {
        case SubmitOrder.Res(Some(order)) =>
          info(s" response ${order}")
          order.status should be(OrderStatus.STATUS_PENDING)
        case _ => assert(false)
      }

      val rawOrder4 =
        createRawOrder(amountS = "323456689".zeros(10), amountB = "1".zeros(18))
      val f4 = singleRequest(SubmitOrder.Req(Some(rawOrder4)), "submit_order")

      val res4 = Await.result(f4, timeout.duration)
      res4 match {
        case SubmitOrder.Res(Some(order)) =>
          info(s" response ${order}")
          order.status should be(OrderStatus.STATUS_PENDING)
        case _ => assert(false)
      }

      //根据不同的level需要有不同的汇总
      val getOrderBook1 = GetOrderbook.Req(
        0,
        100,
        Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))
      )

      val orderbookRes1 = expectOrderbookRes(
        getOrderBook1,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )

      orderbookRes1 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.size == 3)
          assert(
            sells(0).price == "0.309161" &&
              sells(0).amount == "6.46913" &&
              sells(0).total == "2.00000"
          )

          assert(
            sells(1).price == "0.447514" &&
              sells(1).amount == "2.23457" &&
              sells(1).total == "1.00000"
          )

          assert(
            sells(2).price == "0.810001" &&
              sells(2).amount == "1.23457" &&
              sells(2).total == "1.00000"
          )

        case _ => assert(false)
      }

      //下一level
      val getOrderBook2 = GetOrderbook.Req(
        1,
        100,
        Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookRes2 = expectOrderbookRes(
        getOrderBook2,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      orderbookRes2 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.size == 3)
          assert(
            sells(0).price == "0.30917" &&
              sells(0).amount == "6.46913" &&
              sells(0).total == "2.00000"
          )

          assert(
            sells(1).price == "0.44752" &&
              sells(1).amount == "2.23457" &&
              sells(1).total == "1.00000"
          )

          assert(
            sells(2).price == "0.81001" &&
              sells(2).amount == "1.23457" &&
              sells(2).total == "1.00000"
          )

        case _ => assert(false)
      }

    }
  }

}
