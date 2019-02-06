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

package io.lightcone.actors.metadata

import akka.pattern._
import io.lightcone.actors.core._
import io.lightcone.core._
import io.lightcone.actors.support.CommonSpec
import io.lightcone.actors.support._
import io.lightcone.actors.utils.MetadataRefresher
import io.lightcone.proto._

import scala.concurrent.Await

class DynamicAdjustMarketsSpec
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with EthereumSupport
    with DatabaseModuleSupport
    with MetadataManagerSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderGenerateSupport {

  "change a market to mode of TERMINATE" must {
    "return an error with ERR_INVALID_MARKET submiting an order, orderbook" in {

      info("submit an order")
      val amountS = "10"
      val amountB = "1"
      val rawOrder =
        createRawOrder(amountS = amountS.zeros(18), amountB = amountB.zeros(18))
      val f = singleRequest(SubmitOrder.Req(Some(rawOrder)), "submit_order")

      val res = Await.result(f, timeout.duration)
      res match {
        case SubmitOrder.Res(Some(order)) =>
          info(s" response ${order}")
          order.status should be(OrderStatus.STATUS_PENDING)
        case _ => assert(false)
      }

      info("this order must be saved in db.")
      val getOrderF = dbModule.orderService.getOrder(rawOrder.hash)

      val getOrder = Await.result(getOrderF, timeout.duration)
      getOrder match {
        case Some(order) =>
          assert(order.sequenceId > 0)
        case None => assert(false)
      }
      //orderbook
      info("check the status of orderbook now.")
      val getOrderBook = GetOrderbook.Req(
        0,
        100,
        Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookRes = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      orderbookRes match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.nonEmpty)
          assert(
            sells(0).price == "0.100000" &&
              sells(0).amount == "10.00000" &&
              sells(0).total == "1.00000"
          )
          assert(buys.isEmpty)
        case _ => assert(false)
      }

      info("send TERMINATE event")

      val terminateMarketF = actors.get(MetadataManagerActor.name) ? TerminateMarket
        .Req(MarketHash(MarketPair(rawOrder.tokenS, rawOrder.tokenB)).toString)
      Await.result(terminateMarketF, timeout.duration)
      actors.get(MetadataRefresher.name) ! MetadataChanged()
      Thread.sleep(1000) //等待changed事件执行完毕

      info("check the response of submiting an order")
      val rawOrder1 =
        createRawOrder(amountS = amountS.zeros(18), amountB = amountB.zeros(18))
      val f1 = singleRequest(SubmitOrder.Req(Some(rawOrder1)), "submit_order")
      try {
        Await.result(f1, timeout.duration)
      } catch {
        case e: Exception =>
          info(s"can't be submitted, res: ${e.getMessage}")
      }

      info("change this market to ACTIVE")
      val enableMarketF = actors.get(MetadataManagerActor.name) ?
        UpdateMarketMetadata.Req(
          Some(
            metadataManager
              .getMarketMetadata(
                MarketHash(MarketPair(rawOrder.tokenS, rawOrder.tokenB)).toString
              )
              .copy(status = MarketMetadata.Status.ACTIVE)
          )
        )

      Await.result(terminateMarketF, timeout.duration)

      actors.get(MetadataRefresher.name) ! MetadataChanged()
      Thread.sleep(1000)
      val f3 = actors.get(OrderbookManagerActor.name) ? getOrderBook
      Await.result(f3, timeout.duration)

      val f2 = singleRequest(SubmitOrder.Req(Some(rawOrder1)), "submit_order")
      Await.result(f2, timeout.duration)

      val orderbookRes2 = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )

      orderbookRes2 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.nonEmpty)

        case _ => assert(false)
      }

      info("then change this market to READONLY")
      val readonlyMarketF = actors.get(MetadataManagerActor.name) ? UpdateMarketMetadata
        .Req(
          Some(
            metadataManager
              .getMarketMetadata(
                MarketHash(MarketPair(rawOrder.tokenS, rawOrder.tokenB)).toString
              )
              .copy(status = MarketMetadata.Status.READONLY)
          )
        )
      Await.result(terminateMarketF, timeout.duration)
      actors.get(MetadataRefresher.name) ! MetadataChanged()

      Thread.sleep(1000)
      info("can't submit order in mode of READONLY")
      val rawOrder4 =
        createRawOrder(amountS = amountS.zeros(18), amountB = amountB.zeros(18))
      val f4 = singleRequest(SubmitOrder.Req(Some(rawOrder4)), "submit_order")
      try {
        Await.result(f4, timeout.duration)
      } catch {
        case e: Exception =>
          info(s"can't be submitted, res: ${e.getMessage}")
      }

      info("can get response when getOrderbook in mode of READONLY")
      val orderbookRes4 = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )

      orderbookRes4 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.nonEmpty)
        case _ => assert(false)
      }
    }
  }

}