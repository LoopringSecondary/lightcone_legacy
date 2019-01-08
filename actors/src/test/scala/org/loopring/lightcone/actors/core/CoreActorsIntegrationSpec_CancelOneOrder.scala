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

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern._
import akka.util.Timeout
import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.lib.MarketHashProvider
import org.loopring.lightcone.proto
import org.loopring.lightcone.proto._

import scala.concurrent.{Await, ExecutionContext}

class CoreActorsIntegrationSpec_CancelOneOrder
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "order_handler",
                         | "multi_account_manager",
                         | "market_manager",
                         | "orderbook_manager",
                         | "gas_price"]
                         |""".stripMargin)
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with EthereumQueryMockSupport
    with OrderGenerateSupport {

  "submiting one from OrderHandleActor" must {
    "get depth in OrderbookManagerActor" in {
      val rawOrder = createRawOrder()
      val submitF = actors.get(MultiAccountManagerActor.name) ? SubmitOrder.Req(
        Some(rawOrder)
      )
      val submitRes = Await.result(submitF, timeout.duration)
      info(s"submit res: ${submitRes}")

      Thread.sleep(1000)
      actors.get(OrderbookManagerActor.name) ! GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )

      expectMsgPF() {
        case GetOrderbook.Res(Some(orderbook)) =>
          info("----orderbook status after submitted an order: " + orderbook)
          orderbook.sells should not be empty
      }

      info("cancel this order now. ")

      val cancelReq = CancelOrder.Req(
        id = rawOrder.hash,
        owner = rawOrder.owner,
        status = OrderStatus.STATUS_SOFT_CANCELLED_BY_USER,
        marketId = Some(MarketId(rawOrder.tokenS, rawOrder.tokenB))
      )

      val cancelResF = actors.get(MultiAccountManagerActor.name) ? cancelReq

      val cancelRes = Await.result(cancelResF, timeout.duration)
      info(s"submit res: ${cancelRes}")

      Thread.sleep(1000)

      val getOrderFromDbF = dbModule.orderService.getOrder(rawOrder.hash)
      val getOrderFromDb =
        Await.result(getOrderFromDbF.mapTo[Option[RawOrder]], timeout.duration)

      getOrderFromDb map { o =>
        o.getState.status should be(OrderStatus.STATUS_SOFT_CANCELLED_BY_USER)
      }

      actors.get(OrderbookManagerActor.name) ! GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )

      expectMsgPF() {
        case GetOrderbook.Res(Some(orderbook)) =>
          info("----orderbook status after cancel this order: " + orderbook)
          assert(orderbook.sells.isEmpty)
      }
    }

  }

}
