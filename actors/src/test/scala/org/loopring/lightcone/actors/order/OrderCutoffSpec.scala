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
import akka.util.Timeout
import org.loopring.lightcone.actors.core.{
  MultiAccountManagerActor,
  OrderCutoffHandlerActor,
  OrderRecoverCoordinator
}
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto.Orderbook.Item
import org.loopring.lightcone.proto._
import scala.concurrent.duration._
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
                         | "ring_settlement"]
                         |""".stripMargin)
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with EthereumQueryMockSupport
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
      // 1. save some orders in db
      val f = testSaveOrder()
      val res = Await.result(f.mapTo[Seq[SubmitOrder.Res]], timeout.duration)
      res.map { r =>
        r match {
          case SubmitOrder.Res(Some(order)) =>
            info(s" response ${order}")
            order.status should be(OrderStatus.STATUS_PENDING)
          case _ => assert(false)
        }
      }

      // 2. get orders
      val orders1 =
        dbModule.orderService.getOrders(Set(OrderStatus.STATUS_NEW), Set(owner))
      val resOrder1 =
        Await.result(orders1.mapTo[Seq[RawOrder]], timeout.duration)
      assert(resOrder1.length === 10)

      // 3. send cutoff
      val txHash = "0x999"
      val cutoff = CutoffOrder.Req.Cutoff.ByOwner(
        OwnerCutoffs(
          owner = owner,
          cutoff = timeProvider.getTimeSeconds().toInt + 100
        )
      )
      actors.get(OrderCutoffHandlerActor.name) ? CutoffOrder.Req(
        txHash = txHash,
        blockHeight = 1L,
        cutoff = cutoff
      )
      Thread.sleep(5000)

      // 4. select cutoff
      val f2 = dbModule.orderCutoffService.getCutoffByTxHash(txHash)
      val res2 =
        Await.result(f2.mapTo[Option[OrdersCutoffEvent]], timeout.duration)
      res2 match {
        case Some(c) => assert(true)
        case None    => assert(false)
      }

      // 5. get orders
      val orders2 =
        dbModule.orderService.getOrders(Set(OrderStatus.STATUS_NEW), Set(owner))
      val resOrder2 =
        Await.result(orders2.mapTo[Seq[RawOrder]], timeout.duration)
      assert(resOrder2.length === 0)
    }
  }

}
