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

package org.loopring.lightcone.actors.order

import akka.pattern._
import org.loopring.lightcone.actors.core.OrderCutoffHandlerActor
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto.Orderbook.Item
import org.loopring.lightcone.proto._
import scala.concurrent.{Await, Future}

class OrderCutoffSpec
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "order_handler",
                         | "multi_account_manager",
                         | "market_manager",
                         | "orderbook_manager",
                         | "gas_price",
                         | "order_cutoff_handler",
                         | "ethereum_access",
                         | "ring_settlement"]
                         |""".stripMargin)
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with EthereumQueryMockSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderGenerateSupport
    with OrderCutoffSupport {
  val owner = "0xabc0dae0a3e4e146bcaf0fe782be5afb14041a10"

  private def testSaves(orders: Seq[RawOrder]): Future[Seq[Any]] = {
    Future.sequence(orders.map { order ⇒
      singleRequest(SubmitOrder.Req(Some(order)), "submit_order")
    })
  }

  private def testSaveOrder(): Future[Seq[Any]] = {
    val rawOrders = ((0 until 6) map { i =>
      createRawOrder(
        owner = owner,
        amountS = "10".zeros(LRC_TOKEN.decimals),
        amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
      )
    }) ++
      ((0 until 4) map { i =>
        val o = createRawOrder(
          owner = owner,
          amountS = "20".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )
        o.copy(
          state = Some(o.state.get.copy(status = OrderStatus.STATUS_PENDING))
        )
        o
      })
    testSaves(rawOrders)
  }

  "send a cutoff event" must {
    "stored cutoff and cancel all affected orders" in {
      // 1. submit some orders
      val f = testSaveOrder()
      val res = Await.result(f.mapTo[Seq[SubmitOrder.Res]], timeout.duration)
      res.map {
        _ match {
          case SubmitOrder.Res(Some(order)) =>
            info(s" response ${order}")
            order.status should be(OrderStatus.STATUS_PENDING)
          case _ => assert(false)
        }
      }
      Thread.sleep(5000)

      // 2. get orders
      val orders1 =
        dbModule.orderService.getOrders(
          Set(OrderStatus.STATUS_NEW, OrderStatus.STATUS_PENDING),
          Set(owner)
        )
      val resOrder1 =
        Await.result(orders1.mapTo[Seq[RawOrder]], timeout.duration)
      assert(resOrder1.length === 10)

      // 3. orderbook
      val getOrderBook1 = GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF1 = singleRequest(getOrderBook1, "orderbook")
      val orderbookRes1 =
        Await
          .result(orderbookF1.mapTo[GetOrderbook.Res], timeout.duration)
          .orderbook
          .get

      // 4. send cutoff
      val txHash = "0x999"
      val cutoff = OwnerCutoffEvent(
        owner = owner,
        cutoff = timeProvider.getTimeSeconds().toInt + 100
      )
      actors.get(OrderCutoffHandlerActor.name) ? cutoff
      Thread.sleep(5000)

      // 5. get orders
      val orders2 =
        dbModule.orderService.getOrders(
          Set(OrderStatus.STATUS_NEW, OrderStatus.STATUS_PENDING),
          Set(owner)
        )
      val resOrder2 =
        Await.result(orders2.mapTo[Seq[RawOrder]], timeout.duration)
      assert(resOrder2.length === 0)

      // 5. orderbook
      val orderbookF2 = singleRequest(getOrderBook1, "orderbook")
      val orderbookRes2 =
        Await
          .result(orderbookF2.mapTo[GetOrderbook.Res], timeout.duration)
          .orderbook
          .get
      assert(
        orderbookRes1.sells.nonEmpty && orderbookRes1.sells.length === 2 && orderbookRes1.buys.isEmpty
      )
      orderbookRes1.sells.foreach(_ match {
        case Item("10.000000", "60.00000", "6.00000") => assert(true)
        case Item("20.000000", "80.00000", "4.00000") => assert(true)
        case _                                        => assert(false)
      })
      assert(
        orderbookRes2.sells.isEmpty && orderbookRes2.buys.isEmpty
      )
    }
  }

}
