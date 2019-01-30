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

package org.loopring.lightcone.actors.recover

import akka.pattern._
import akka.util.Timeout
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto.Orderbook.Item
import org.loopring.lightcone.proto._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class RecoverOrderSpec
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
    with RecoverSupport {

  import OrderStatus._

  private def testSaves(
      orders: Seq[RawOrder]
    ): Future[Seq[Either[RawOrder, ErrorCode]]] = {
    for {
      result <- Future.sequence(orders.map { order =>
        dbModule.orderService.saveOrder(order)
      })
    } yield result
  }

  private def testSaveOrder4Recover(
    ): Future[Seq[Either[RawOrder, ErrorCode]]] = {
    val rawOrders = ((0 until 6) map { i =>
      createRawOrder(
        amountS = "10".zeros(LRC_TOKEN.decimals),
        amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
      )
    }) ++
      ((0 until 4) map { i =>
        val o = createRawOrder(
          amountS = "20".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )
        o.withStatus(STATUS_PENDING)
      }) ++
      ((0 until 3) map { i =>
        val o = createRawOrder(
          tokenS = "0x021",
          tokenB = "0x022",
          amountS = "11".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )
        o.withStatus(STATUS_EXPIRED)

      }) ++
      ((0 until 5) map { i =>
        val o = createRawOrder(
          tokenS = "0x031",
          tokenB = "0x032",
          amountS = "12".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )
        o.withStatus(STATUS_DUST_ORDER)

      }) ++
      ((0 until 2) map { i =>
        val o = createRawOrder(
          tokenS = "0x041",
          tokenB = "0x042",
          amountS = "13".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )
        o.withStatus(STATUS_PARTIALLY_FILLED)
      })
    testSaves(rawOrders)
  }

  "recover an address" must {
    "get all effective orders and recover" in {
      val owner = "0xb7e0dae0a3e4e146bcaf0fe782be5afb14041a10"
      // 1. select depth
      val getOrderBook1 = GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF1 = singleRequest(getOrderBook1, "get_orderbook")
      val timeout1 = Timeout(5 second)
      val orderbookRes1 =
        Await
          .result(orderbookF1.mapTo[GetOrderbook.Res], timeout1.duration)
          .orderbook
          .get

      // 2. save some orders in db
      testSaveOrder4Recover()
      // 3. recover
      val marketLrcWeth = Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      val marketMock4 = Some(MarketId("0x041", "0x042"))
      val request1 = ActorRecover.Request(
        addressShardingEntity = MultiAccountManagerActor
          .getEntityId(owner, 100),
        marketId = marketLrcWeth
      )
      implicit val timeout = Timeout(100 second)
      val r = actors.get(OrderRecoverCoordinator.name) ? request1
      val res = Await.result(r, timeout.duration)
      // 4. get depth
      Thread.sleep(5000)
      val orderbookF2 = singleRequest(getOrderBook1, "get_orderbook")
      val orderbookRes2 =
        Await
          .result(orderbookF2.mapTo[GetOrderbook.Res], timeout1.duration)
          .orderbook
          .get
      assert(orderbookRes1.sells.isEmpty && orderbookRes1.buys.isEmpty)
      assert(
        orderbookRes2.sells.nonEmpty && orderbookRes2.sells.length === 2 && orderbookRes2.buys.isEmpty
      )
      orderbookRes2.sells should be(
        Seq(
          Item("0.050000", "80.00000", "4.00000"),
          Item("0.100000", "60.00000", "6.00000")
        )
      )
    }
  }

}
