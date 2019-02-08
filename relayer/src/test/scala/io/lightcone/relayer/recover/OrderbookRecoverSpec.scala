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

package io.lightcone.relayer.recover

import akka.actor.PoisonPill
import akka.pattern._
import io.lightcone.relayer.actors._
import io.lightcone.relayer.support._
import io.lightcone.relayer.validator._
import io.lightcone.relayer.data._
import io.lightcone.core._
import scala.concurrent.{Await, Future}

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

  import OrderStatus._

  val account1 = getUniqueAccountWithoutEth
  val account2 = getUniqueAccountWithoutEth

  override def beforeAll(): Unit = {
    val f = Future.sequence(
      Seq(
        transferEth(account1.getAddress, "100")(accounts(0)),
        transferLRC(account1.getAddress, "500")(accounts(0)),
        approveLRCToDelegate("2000")(account1),
        transferEth(account2.getAddress, "100")(accounts(0)),
        transferWETH(account2.getAddress, "100")(accounts(0)),
        transferLRC(account2.getAddress, "500")(accounts(0)),
        approveLRCToDelegate("2000")(account2),
        approveWETHToDelegate("1000")(account2)
      )
    )

    Await.result(f, timeout.duration)
    super.beforeAll()
  }

  "recover an orderbook" must {
    "get market's orderbook updates" in {
      info("submit some orders for test")
      val f = testSubmitOrders()
      val res = Await.result(f, timeout.duration)
      res.map {
        case SubmitOrder.Res(Some(order)) =>
          info(s" response ${order}")
          order.status should be(STATUS_PENDING)
        case _ => assert(false)
      }

      info("get orders from db to check submit result")
      val orders1 =
        dbModule.orderService.getOrders(
          Set(STATUS_NEW, STATUS_PENDING),
          Set(account1.getAddress, account2.getAddress)
        )
      val resOrder1 =
        Await.result(orders1.mapTo[Seq[RawOrder]], timeout.duration)
      assert(resOrder1.length === 7)

      info("get orderbook from orderbookManagerActor")
      Thread.sleep(2000)
      val marketPair = MarketPair(LRC_TOKEN.address, WETH_TOKEN.address)
      val getOrderBook1 = GetOrderbook.Req(0, 100, Some(marketPair))
      val orderbookF1 = singleRequest(getOrderBook1, "get_orderbook")
      val orderbookRes1 =
        Await.result(orderbookF1.mapTo[GetOrderbook.Res], timeout.duration)
      checkOrderbookAsExpected(orderbookRes1.orderbook)

      info(
        "get orderbookUpdate from marketManagerActor(stored in marketManager)"
      )
      val r1 = Await.result(
        (actors.get(MarketManagerActor.name) ? GetOrderbookSlots
          .Req(Some(marketPair), 100)).mapTo[GetOrderbookSlots.Res],
        timeout.duration
      )
      val orderbookRes = r1.update.getOrElse(Orderbook.Update())
      checkOrderbookUpdateAsExpected(orderbookRes)

      info("kill orderbookManagerActor")
      // TODO(du) 只测试了shard的actor死掉的情况，包括coordinator在内所有的actor挂掉的情况没有正确的测试，类似初始化加载的过程
      //      val cluster = Cluster(system)
      //      cluster.leave(cluster.selfAddress)
      system.actorSelection(
        "akka://Lightcone/system/sharding/orderbook_manager/orderbook_manager_464977589/orderbook_manager_464977589"
      ) ! PoisonPill
      system.actorSelection(
        "akka://Lightcone/system/sharding/orderbook_manager/orderbook_manager_1749215251/orderbook_manager_1749215251"
      ) ! PoisonPill
      // actors.get(OrderbookManagerActor.name) ! ShardRegion.GracefulShutdown
      //      val region =
      //        ClusterSharding(system).shardRegion(OrderbookManagerActor.name)
      //      region ! ShardRegion.GracefulShutdown
      actors.get(OrderbookManagerMessageValidator.name) ! PoisonPill
      actors.del(OrderbookManagerMessageValidator.name)
      actors.del(OrderbookManagerActor.name)
      Thread.sleep(1000)

      info("get orderbook will got timeout after delete orderbookManagerActor")
      val orderbookF2 = singleRequest(getOrderBook1, "get_orderbook")
      try {
        val orderbookRes2 =
          Await.result(orderbookF2.mapTo[GetOrderbook.Res], timeout.duration)
        assert(false)
      } catch {
        case e: ErrorException
            if e.error.code == ErrorCode.ERR_INTERNAL_UNKNOWN && e
              .getMessage()
              .indexOf("not found actor: orderbook_manager_validator") > -1 =>
          assert(true)
        case e: Throwable if e.getMessage.indexOf("timed out") > -1 =>
          assert(true)
        case e: Throwable =>
          assert(false)
      }

      info("restart orderbookManagerActor")
      startOrderbookSupport
      Thread.sleep(3000)
      assert(actors.contains(OrderbookManagerActor.name))

      info("resend orderbook request")
      val orderbookF3 = singleRequest(getOrderBook1, "get_orderbook")
      val orderbookRes3 =
        Await.result(orderbookF3.mapTo[GetOrderbook.Res], timeout.duration)
      checkOrderbookAsExpected(orderbookRes3.orderbook)
    }
  }

  private def checkOrderbookAsExpected(orderbook: Option[Orderbook]): Unit = {
    orderbook match {
      case Some(Orderbook(lastPrice, sells, buys)) =>
        assert(sells.nonEmpty && sells.length == 4)
        assert(buys.nonEmpty && buys.length == 3)
        //Vector(Item(0.010000,100.00000,1.00000), Item(0.047620,21.00000,1.00000), Item(0.050000,20.00000,1.00000), Item(0.176471,17.00000,3.00000))
        sells.foreach {
          case Orderbook.Item("0.010000", "100.00000", "1.00000") =>
            assert(true)
          case Orderbook.Item("0.047620", "21.00000", "1.00000") =>
            assert(true)
          case Orderbook.Item("0.050000", "20.00000", "1.00000") =>
            assert(true)
          case Orderbook.Item("0.176471", "17.00000", "3.00000") =>
            assert(true)
          case _ => assert(false)
        }
        //Vector(Item(0.004285,700.00000,3.00000), Item(0.003939,1777.00000,7.00000), Item(0.003333,300.00000,1.00000))
        buys.foreach {
          case Orderbook.Item("0.004285", "700.00000", "3.00000") =>
            assert(true)
          case Orderbook.Item("0.003939", "1777.00000", "7.00000") =>
            assert(true)
          case Orderbook.Item("0.003333", "300.00000", "1.00000") =>
            assert(true)
          case _ => assert(false)
        }
      case _ => assert(false)
    }
  }

  private def checkOrderbookUpdateAsExpected(update: Orderbook.Update): Unit = {
    assert(update.sells.nonEmpty && update.sells.length == 4)
    assert(update.buys.nonEmpty && update.buys.length == 3)
    update.sells.foreach {
      case Orderbook.Slot(10000, 100.0, 1.0) =>
        assert(true)
      case Orderbook.Slot(47620, 21.0, 1.0) =>
        assert(true)
      case Orderbook.Slot(50000, 20.0, 1.0) =>
        assert(true)
      case Orderbook.Slot(176471, 17.0, 3.0) =>
        assert(true)
      case _ => assert(false)
    }
    update.buys.foreach {
      case Orderbook.Slot(4285, 700.0, 3.0) =>
        assert(true)
      case Orderbook.Slot(3939, 1777.0, 7.0) =>
        assert(true)
      case Orderbook.Slot(3333, 300.0, 1.0) =>
        assert(true)
      case _ => assert(false)
    }
  }

  private def testSubmitOrders(): Future[Seq[SubmitOrder.Res]] = {
    //sells:Vector(Item(0.010000,100.00000,1.00000), Item(0.047620,21.00000,1.00000), Item(0.050000,20.00000,1.00000), Item(0.176471,17.00000,3.00000)),
    //buys:Vector(Item(0.004285,700.00000,3.00000), Item(0.003939,1777.00000,7.00000), Item(0.003333,300.00000,1.00000))
    val rawOrders =
      Seq(
        createRawOrder( // sell 20 LRC, price = 1/20 = 0.05
          tokenB = WETH_TOKEN.address,
          tokenS = LRC_TOKEN.address,
          amountB = "1".zeros(WETH_TOKEN.decimals),
          amountS = "20".zeros(LRC_TOKEN.decimals),
          amountFee = 2.toString.zeros(LRC_TOKEN.decimals)
        )(account1),
        createRawOrder( // sell 21 LRC, price = 1/21 = 0.047620
          tokenB = WETH_TOKEN.address,
          tokenS = LRC_TOKEN.address,
          amountB = "1".zeros(WETH_TOKEN.decimals),
          amountS = "21".zeros(LRC_TOKEN.decimals),
          amountFee = 3.toString.zeros(LRC_TOKEN.decimals)
        )(account1),
        createRawOrder( // sell 100 LRC, price = 1/100 = 0.01
          tokenB = WETH_TOKEN.address,
          tokenS = LRC_TOKEN.address,
          amountB = "1".zeros(WETH_TOKEN.decimals),
          amountS = "100".zeros(LRC_TOKEN.decimals),
          amountFee = 4.toString.zeros(LRC_TOKEN.decimals)
        )(account1),
        createRawOrder( // sell 17 LRC, price = 3/17 = 0.176471
          tokenB = WETH_TOKEN.address,
          tokenS = LRC_TOKEN.address,
          amountB = "3".zeros(WETH_TOKEN.decimals),
          amountS = "17".zeros(LRC_TOKEN.decimals),
          amountFee = 3.toString.zeros(LRC_TOKEN.decimals)
        )(account1),
        createRawOrder( // buy 300 LRC, price = 1/300 = 0.003333
          tokenS = WETH_TOKEN.address,
          tokenB = LRC_TOKEN.address,
          amountS = "1".zeros(WETH_TOKEN.decimals),
          amountB = "300".zeros(LRC_TOKEN.decimals),
          amountFee = 1.toString.zeros(LRC_TOKEN.decimals)
        )(account2),
        createRawOrder( // buy 1777 LRC, price = 7/1777 = 0.003939
          tokenS = WETH_TOKEN.address,
          tokenB = LRC_TOKEN.address,
          amountS = "7".zeros(WETH_TOKEN.decimals),
          amountB = "1777".zeros(LRC_TOKEN.decimals),
          amountFee = 1.toString.zeros(LRC_TOKEN.decimals)
        )(account2),
        createRawOrder( // buy 700 LRC, price = 3/700 = 0.00428
          tokenS = WETH_TOKEN.address,
          tokenB = LRC_TOKEN.address,
          amountS = "3".zeros(WETH_TOKEN.decimals),
          amountB = "700".zeros(LRC_TOKEN.decimals),
          amountFee = 1.toString.zeros(LRC_TOKEN.decimals)
        )(account2)
      )
    submitOrders(rawOrders)
  }

  def submitOrders(orders: Seq[RawOrder]): Future[Seq[SubmitOrder.Res]] = {
    Future.sequence(orders.map { order =>
      singleRequest(SubmitOrder.Req(Some(order)), "submit_order")
        .mapTo[SubmitOrder.Res]
    })
  }

}
