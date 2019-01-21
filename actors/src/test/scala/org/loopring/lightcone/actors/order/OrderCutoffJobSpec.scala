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

import java.util.concurrent.TimeUnit
import akka.pattern._
import org.loopring.lightcone.actors.core.{
  MetadataManagerActor,
  OrderCutoffHandlerActor
}
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._
import org.rnorth.ducttape.TimeoutException
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.containers.ContainerLaunchException
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class OrderCutoffJobSpec
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
      info("submit some orders")
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

      info("get orders")
      val orders1 =
        dbModule.orderService.getOrders(
          Set(OrderStatus.STATUS_NEW, OrderStatus.STATUS_PENDING),
          Set(owner)
        )
      val resOrder1 =
        Await.result(orders1.mapTo[Seq[RawOrder]], timeout.duration)
      assert(resOrder1.length === 10)

      info(" check orderbook")
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

      info("save 2 cutoff jobs")
      val now = timeProvider.getTimeMillis
      val cutoffValue = timeProvider.getTimeSeconds() + 100
      val job = OrderCutoffJob(
        owner = owner,
        cutoff = cutoffValue,
        createAt = now
      )
      val r1 = dbModule.orderCutoffJobDal.saveJob(job)
      val res1 = Await.result(r1.mapTo[Boolean], 5 second)
      assert(res1)
      val r2 = dbModule.orderCutoffJobDal.saveJob(job.copy(tradingPair = "0x1"))
      val res2 = Await.result(r2.mapTo[Boolean], 5 second)
      assert(res2)
      val q1 = dbModule.orderCutoffJobDal.getJobs()
      val q11 = Await.result(q1.mapTo[Seq[OrderCutoffJob]], 5 second)
      assert(q11.length == 2)

      info("run cutoffHandlerActor will handle the cutoff jobs")
      actors.add(OrderCutoffHandlerActor.name, OrderCutoffHandlerActor.start)
      Thread.sleep(3000)

      try Unreliables.retryUntilTrue(
        10,
        TimeUnit.SECONDS,
        () => {
          val f =
            (actors.get(OrderCutoffHandlerActor.name) ? OrdersCancelledEvent())
              .mapTo[CancelOrders.Res]
          val res = Await.result(f, timeout.duration)
          res.cancelledOrderHashes.isEmpty
        }
      )
      catch {
        case e: TimeoutException =>
          throw new ContainerLaunchException(
            "Timed out waiting for connectionPools init.)"
          )
      }

      info("get orderbook first，then waiting for cutoffevent finished")
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

      info("get orders")
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
