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
import org.loopring.lightcone.proto._

import scala.concurrent.{Await, Future}

class OrderCutoffSpec
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
    with OrderGenerateSupport
    with OrderCutoffSupport {
  val owner = accounts(0).getAddress

  private def testSaves(orders: Seq[RawOrder]): Future[Seq[Any]] = {
    Future.sequence(orders.map { order =>
      singleRequest(SubmitOrder.Req(Some(order)), "submit_order")
    })
  }

  private def testSaveOrder(): Future[Seq[Any]] = {
    val rawOrders = ((0 until 6) map { i =>
      createRawOrder(
        amountS = "10".zeros(LRC_TOKEN.decimals),
        amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
      )(accounts(0))
    }) ++
      ((0 until 4) map { i =>
        val o = createRawOrder(
          amountS = "20".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )(accounts(0))
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

      val orderbookRes1 = expectOrderbookRes(
        getOrderBook1,
        (orderbook: Orderbook) =>
          orderbook.sells.nonEmpty && orderbook.sells.length === 2
      )
      orderbookRes1 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(
            sells(0).price == "10.000000" &&
              sells(0).amount == "60.00000" &&
              sells(0).total == "6.00000"
          )
          assert(
            sells(1).price == "20.000000" &&
              sells(1).amount == "80.00000" &&
              sells(1).total == "4.00000"
          )
        case _ => assert(false)
      }

      val orderbookF1 = singleRequest(getOrderBook1, "orderbook")

      // 4. send cutoff
      val txHash = "0x999"
      val cutoff = CutoffEvent(
        owner = owner,
        cutoff = timeProvider.getTimeSeconds().toInt + 100
      )
      actors.get(OrderCutoffHandlerActor.name) ? cutoff

      // 5. get orderbook first， waiting cutoffevent finish
      val orderbookRes2 = expectOrderbookRes(
        getOrderBook1,
        (orderbook: Orderbook) => orderbook.sells.isEmpty
      )
      orderbookRes2 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.isEmpty && buys.isEmpty)
        case _ => assert(false)
      }

      // 6. get orders
      val orders2 =
        dbModule.orderService.getOrders(
          Set(OrderStatus.STATUS_NEW, OrderStatus.STATUS_PENDING),
          Set(owner)
        )
      val resOrder2 =
        Await.result(orders2.mapTo[Seq[RawOrder]], timeout.duration)
      assert(resOrder2.length === 0)

    }
  }

}
