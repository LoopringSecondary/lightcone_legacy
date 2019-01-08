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

package org.loopring.lightcone.actors.core

import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._
import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class RingSettlementSpec
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "multi_account_manager",
                         | "ethereum_query",
                         | "order_handler",
                         | "ring_settlement",
                         | "gas_price",
                         | "ethereum_client_monitor",
                         | "orderbook_manager",
                         | "market_manager"]
                         |""".stripMargin)
    with EthereumSupport
    with MarketManagerSupport
    with MultiAccountManagerSupport
    with OrderGenerateSupport
    with OrderHandleSupport
    with OrderbookManagerSupport
    with JsonrpcSupport
    with HttpSupport {

  def selfConfig = config.getConfig(RingSettlementManagerActor.name)
  def orderHandler = actors.get(OrderPersistenceActor.name)

  val users = selfConfig
    .getConfigList("users")
    .asScala
    .map(config => config.getString("addr") -> config.getString("key"))

  "Submit a ring tx " must {
    "tx successfully, order, balance, allowance must be right" in {

      val getBaMethod = "get_balance_and_allowance"
      val submit_order = "submit_order"
      val getOrderBook1 = GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val getBalanceReqs =
        users.unzip._1.map(
          user =>
            GetBalanceAndAllowances.Req(
              user,
              tokens =
                Seq(LRC_TOKEN.address, WETH_TOKEN.address, GTO_TOKEN.address)
            )
        )

      val order1 = createRawOrder(owner = users.head._1)(Some(users.head._2))
      val order2 = createRawOrder(
        owner = users(1)._1,
        tokenB = LRC_TOKEN.address,
        tokenS = WETH_TOKEN.address,
        amountB = "10".zeros(18),
        amountS = "1".zeros(18)
      )(Some(users(1)._2))

      val submitOrder1F = singleRequest(
        SubmitOrder.Req(Some(order1)),
        submit_order
      ).mapAs[SubmitOrder.Res]
      Await.result(submitOrder1F, timeout.duration)

      val orderbook1F = singleRequest(
        getOrderBook1,
        "orderbook"
      ).mapAs[GetOrderbook.Res]
        .map(_.getOrderbook)
      val orderbook1 = Await.result(orderbook1F, timeout.duration)

      assert(orderbook1.buys.isEmpty)
      assert(orderbook1.sells.size == 1)
      orderbook1.sells.head match {
        case Orderbook.Item(price, amount, total) =>
          assert(amount.toDouble == "10".toDouble)
          assert(price.toDouble == "10".toDouble)
          assert(total.toDouble == "1".toDouble)
        case _ =>
      }

      val submitOrder2F = singleRequest(
        SubmitOrder.Req(Some(order2)),
        submit_order
      ).mapAs[SubmitOrder.Res]
      Await.result(submitOrder2F, timeout.duration)
      Thread.sleep(1000)
      val orderbookF2 = singleRequest(
        getOrderBook1,
        "orderbook"
      ).mapAs[GetOrderbook.Res]
        .map(_.getOrderbook)

      val orderbook2 = Await.result(orderbookF2, timeout.duration)

      println(orderbook2)
//      assert(orderbook2.buys.isEmpty)
//      assert(orderbook2.sells.isEmpty)
    }
  }

}
