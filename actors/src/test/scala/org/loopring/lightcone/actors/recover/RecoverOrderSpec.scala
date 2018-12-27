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

import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.core.{
  MarketManagerActor,
  MultiAccountManagerActor,
  OrderRecoverCoordinator
}
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.lib.{MarketHashProvider, SystemTimeProvider}
import org.loopring.lightcone.proto._
import scala.concurrent.{Await, Future}
import akka.pattern._
import akka.util.Timeout
import scala.concurrent.duration._
import org.loopring.lightcone.core.base._
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric

class RecoverOrderSpec
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "order_handler",
                         | "multi_account_manager",
                         | "market_manager",
                         | "orderbook_manager",
                         | "gas_price",
                         | "order_recover",
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
    with RecoverSupport {
  val tokenS = "0xaaaaaa1"
  val tokenB = "0xbbbbbb1"
  val tokenFee = "0x-fee-token"
  val validSince = 1
  val validUntil = timeProvider.getTimeSeconds()

  private def testSaves(
      orders: Seq[XRawOrder]
    ): Future[Seq[Either[XRawOrder, XErrorCode]]] = {
    for {
      result ← Future.sequence(orders.map { order ⇒
        dbModule.orderService.saveOrder(order)
      })
    } yield result
  }

  private def testSaveOrder4Recover(
    ): Future[Seq[Either[XRawOrder, XErrorCode]]] = {
    val rawOrders = ((0 until 6) map { i =>
      createRawOrder(
        amountS = "10".zeros(LRC_TOKEN.decimals),
        amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
      )
    }) ++
      ((0 until 4) map { i =>
        createRawOrder(
          amountS = "20".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        ).copy(
          state = Some(
            XRawOrder.State(
              status = XOrderStatus.STATUS_PENDING
            )
          )
        )
      }) ++
      ((0 until 3) map { i =>
        createRawOrder(
          tokenS = "0x021",
          tokenB = "0x022",
          amountS = "11".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        ).copy(
          state = Some(
            XRawOrder.State(
              status = XOrderStatus.STATUS_EXPIRED
            )
          )
        )
      }) ++
      ((0 until 5) map { i =>
        createRawOrder(
          tokenS = "0x031",
          tokenB = "0x032",
          amountS = "12".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        ).copy(
          state = Some(
            XRawOrder.State(
              status = XOrderStatus.STATUS_DUST_ORDER
            )
          )
        )
      }) ++
      ((0 until 2) map { i =>
        createRawOrder(
          tokenS = "0x041",
          tokenB = "0x042",
          amountS = "13".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        ).copy(
          state = Some(
            XRawOrder.State(
              status = XOrderStatus.STATUS_PARTIALLY_FILLED
            )
          )
        )
      })
    testSaves(rawOrders)
  }

  "recover an address" must {
    "get all effective orders and recover" in {
      val owner = "0xb7e0dae0a3e4e146bcaf0fe782be5afb14041a10"
      // 1. select depth
      val getOrderBook1 = XGetOrderbook(
        0,
        100,
        Some(XMarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF1 = singleRequest(
        getOrderBook1,
        "orderbook"
      )
      val timeout1 = Timeout(5 second)
      val orderbookRes1 = Await.result(orderbookF1, timeout1.duration)

      // 2. save some orders in db
      testSaveOrder4Recover()
      // 3. recover
      val marketLrcWeth = Some(
        XMarketId(primary = LRC_TOKEN.address, secondary = WETH_TOKEN.address)
      )
      val marketMock4 = Some(XMarketId(primary = "0x041", secondary = "0x042"))
      val request1 = XRecover.Request(
        addressShardingEntity = MultiAccountManagerActor
          .getEntityId(owner, 100),
        marketId = marketLrcWeth
      )
      // implicit val timeout = Timeout(100 second)
      val r = actors.get(OrderRecoverCoordinator.name) ? request1
      val res = Await.result(r, timeout.duration)
      res match {
        case XRecover.Finished(b) => assert(b)
        case _                    => assert(false)
      }
      // 4. get depth
      Thread.sleep(10000)
      val orderbookRes2 = Await.result(orderbookF1, timeout1.duration)
      println(11111, orderbookRes1)
      println(22222, orderbookRes1)
    }
  }

}
