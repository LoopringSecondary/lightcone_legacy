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

import akka.actor.PoisonPill
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.pattern._
import akka.util.Timeout
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.actors.validator.OrderbookManagerMessageValidator
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto.Orderbook.Item
import org.loopring.lightcone.proto._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.math.BigInt

class OrderbookRecoverSpec
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

  val account1 = getUniqueAccountWithoutEth
  val account2 = getUniqueAccountWithoutEth

  override def beforeAll(): Unit = {
    val f = Future.sequence(
      Seq(
        transferEth(account1.getAddress, "10")(accounts(0)),
        transferLRC(account1.getAddress, "200")(accounts(0)),
        approveLRCToDelegate("2000")(account1),
        transferEth(account2.getAddress, "10")(accounts(0)),
        transferWETH(account2.getAddress, "100")(accounts(0)),
        transferLRC(account2.getAddress, "200")(accounts(0)),
        approveLRCToDelegate("2000")(account2),
        approveWETHToDelegate("1000")(account2)
      )
    )

    Await.result(f, timeout.duration)
    super.beforeAll()
  }

  private def testSaves(orders: Seq[RawOrder]): Future[Seq[Any]] = {
    Future.sequence(orders.map { order =>
      singleRequest(SubmitOrder.Req(Some(order)), "submit_order")
    })
  }

  private def testSaveOrder(): Future[Seq[Any]] = {
    val rawOrders =

      ((0 until 1) map { i =>
        val o = createRawOrder( // sell 20 LRC, price = 1/20 = 0.05
          tokenB = WETH_TOKEN.address,
          tokenS = LRC_TOKEN.address,
          amountB = "1".zeros(WETH_TOKEN.decimals),
          amountS = "20".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )(account2)
        o.copy(
          state = Some(o.state.get.copy(status = OrderStatus.STATUS_PENDING))
        )
        o
      }) ++
        ((0 until 1) map { i =>
          val o = createRawOrder( // buy 30 LRC, price = 1/30 = 0.033333
            tokenB = LRC_TOKEN.address,
            tokenS = WETH_TOKEN.address,
            amountB = "30".zeros(LRC_TOKEN.decimals),
            amountS = "1".zeros(WETH_TOKEN.decimals),
            amountFee = (i + 1).toString.zeros(LRC_TOKEN.decimals)
          )(account1)
          o.copy(
            state = Some(o.state.get.copy(status = OrderStatus.STATUS_PENDING))
          )
          o
        })
    //    ++ ((0 until 1) map { i =>
    //        createRawOrder(
    //          amountS = "10".zeros(LRC_TOKEN.decimals),
    //          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
    //        )(account1)
    //      })
    //      ((0 until 1) map { i =>
    //        val o = createRawOrder(
    //          tokenS = WETH_TOKEN.address,
    //          tokenB = LRC_TOKEN.address,
    //          amountS = "1".zeros(WETH_TOKEN.decimals),
    //          amountB = "121".zeros(LRC_TOKEN.decimals),
    //          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
    //        )(account2)
    //        o.copy(
    //          state = Some(o.state.get.copy(status = OrderStatus.STATUS_PENDING))
    //        )
    //        o
    //      })
    testSaves(rawOrders)
  }

  "recover an orderbook" must {
    "get market's orderbook updates" in {
      info("submit some orders")
      val f = testSaveOrder()
      val res = Await.result(f.mapTo[Seq[SubmitOrder.Res]], timeout.duration)
      res.map {
        case SubmitOrder.Res(Some(order)) =>
          info(s" response ${order}")
          order.status should be(OrderStatus.STATUS_PENDING)
        case _ => assert(false)
      }

      info("get orders")
      val orders1 =
        dbModule.orderService.getOrders(
          Set(OrderStatus.STATUS_NEW, OrderStatus.STATUS_PENDING),
          Set(account1.getAddress, account2.getAddress)
        )
      val resOrder1 =
        Await.result(orders1.mapTo[Seq[RawOrder]], timeout.duration)
      //assert(resOrder1.length === 12)

      info("get orderbook from orderbookManagerActor")

      Thread.sleep(2000)
      val marketId = MarketId(LRC_TOKEN.address, WETH_TOKEN.address)
      val getOrderBook1 = GetOrderbook.Req(0, 100, Some(marketId))
      val orderbookF1 = singleRequest(getOrderBook1, "get_orderbook")
      val orderbookRes1 =
        Await.result(orderbookF1.mapTo[GetOrderbook.Res], timeout.duration)

      orderbookRes1.orderbook match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          println(s"~~~~~~sells:${sells}, \nbuys:${buys}")
          assert(sells.nonEmpty && buys.nonEmpty)
          assert(
            buys(0).price == "0.033334" &&
              buys(0).amount == "30.00000" &&
              buys(0).total == "1.00000"
          )
          assert(
            sells(0).price == "0.050000" &&
              sells(0).amount == "20.00000" &&
              sells(0).total == "1.00000"
          )
        case _ => assert(false)
      }

      //      info(
      //        "get orderbookUpdate from marketManagerActor(stored in marketManager)"
      //      )
      //      val r1 = Await.result(
      //        (actors.get(MarketManagerActor.name) ? GetOrderbookSlots.Req(
      //          Some(marketId)
      //        )).mapTo[GetOrderbookSlots.Res],
      //        timeout.duration
      //      )
      //      r1.update.getOrElse(Orderbook.Update()) match {
      //        case Orderbook.Update(sells, buys, _, _) =>
      //          assert(
      //            sells(0).slot == 10000000 &&
      //              sells(0).amount == 60 &&
      //              sells(0).total == 6
      //          )
      //          assert(
      //            sells(1).slot == 20000000 &&
      //              sells(1).amount == 80 &&
      //              sells(1).total == 4
      //          )
      //        case _ => assert(false)
      //      }
      //
      //      info("kill orderbookManagerActor")
      ////      val cluster = Cluster(system)
      ////      cluster.leave(cluster.selfAddress)
      //      system.actorSelection(
      //        "akka://Lightcone/system/sharding/orderbook_manager/orderbook_manager_464977589/orderbook_manager_464977589"
      //      ) ! PoisonPill
      //      system.actorSelection(
      //        "akka://Lightcone/system/sharding/orderbook_manager/orderbook_manager_1749215251/orderbook_manager_1749215251"
      //      ) ! PoisonPill
      //      // actors.get(OrderbookManagerActor.name) ! ShardRegion.GracefulShutdown
      ////      val region =
      ////        ClusterSharding(system).shardRegion(OrderbookManagerActor.name)
      ////      region ! ShardRegion.GracefulShutdown
      //      actors.get(OrderbookManagerMessageValidator.name) ! PoisonPill
      //      actors.del(OrderbookManagerMessageValidator.name)
      //      actors.del(OrderbookManagerActor.name)
      //      Thread.sleep(5000)
      //
      //      info("get orderbook will got timeout after delete orderbookManagerActor")
      //      val orderbookF2 = singleRequest(getOrderBook1, "get_orderbook")
      //      try {
      //        val orderbookRes2 =
      //          Await.result(orderbookF2.mapTo[GetOrderbook.Res], timeout.duration)
      //        assert(false)
      //      } catch {
      //        case e: ErrorException
      //            if e.error.code == ErrorCode.ERR_INTERNAL_UNKNOWN && e
      //              .getMessage()
      //              .indexOf("not found actor: orderbook_manager_validator") > -1 =>
      //          assert(true)
      //        case e: Throwable if e.getMessage.indexOf("timed out") > -1 =>
      //          assert(true)
      //        case e: Throwable =>
      //          assert(false)
      //      }
      //      Thread.sleep(3000)
      //
      //      info("restart orderbookManagerActor")
      //      startOrderbookSupport
      //
      //      assert(actors.contains(OrderbookManagerActor.name))
      //
      //      info("resend orderbook request")
      //      val orderbookF3 = singleRequest(getOrderBook1, "get_orderbook")
      //      val orderbookRes3 =
      //        Await.result(orderbookF3.mapTo[GetOrderbook.Res], timeout.duration)
      //      orderbookRes3.orderbook match {
      //        case Some(Orderbook(lastPrice, sells, buys)) =>
      //          info(s"sells:${sells}, buys:${buys}")
      //          assert(
      //            sells(0).price == "10.000000" &&
      //              sells(0).amount == "60.00000" &&
      //              sells(0).total == "6.00000"
      //          )
      //          assert(
      //            sells(1).price == "20.000000" &&
      //              sells(1).amount == "80.00000" &&
      //              sells(1).total == "4.00000"
      //          )
      //        case _ => assert(false)
      //      }

    }
  }

}
