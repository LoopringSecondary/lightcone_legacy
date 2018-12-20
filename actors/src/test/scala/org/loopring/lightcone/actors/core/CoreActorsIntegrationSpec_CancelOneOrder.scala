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
    with EthereumQueryMockSupport {

  "submiting one from OrderHandleActor" must {
    "get depth in OrderbookManagerActor" in {
      val rawOrder = XRawOrder(
        owner = "0xa",
        hash = "0xa112c",
        version = 1,
        tokenS = LRC_TOKEN.address,
        tokenB = WETH_TOKEN.address,
        amountS = ByteString.copyFrom("10".zeros(18).toString(), "UTF-8"),
        amountB = ByteString.copyFrom("1".zeros(18).toString(), "UTF-8"),
        validSince = 1000,
        state = Some(
          XRawOrder.State(
            createdAt = timeProvider.getTimeMillis,
            updatedAt = timeProvider.getTimeMillis,
            status = XOrderStatus.STATUS_NEW
          )
        ),
        feeParams = Some(
          XRawOrder.FeeParams(
            tokenFee = LRC_TOKEN.address,
            amountFee = ByteString.copyFrom("3".zeros(18).toString(), "utf-8")
          )
        ),
        params = Some(XRawOrder.Params(validUntil = 2000)),
        marketHash =
          MarketHashProvider.convert2Hex(LRC_TOKEN.address, WETH_TOKEN.address)
      )

      val submitF = actors.get(OrderHandlerActor.name) ? XSubmitOrderReq(
        Some(rawOrder)
      )
      val submitRes = Await.result(submitF, timeout.duration)
      info(s"submit res: ${submitRes}")

      Thread.sleep(1000)
      actors.get(OrderbookManagerActor.name) ! XGetOrderbook(
        0,
        100,
        Some(XMarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )

      expectMsgPF() {
        case a: XOrderbook =>
          info("----orderbook status after submitted an order: " + a)
          a.sells should not be empty
      }

      info("cancel this order now. ")

      val cancelReq = XCancelOrderReq(
        id = rawOrder.hash,
        owner = rawOrder.owner,
        status = XOrderStatus.STATUS_CANCELLED_BY_USER,
        marketId = Some(XMarketId(rawOrder.tokenS, rawOrder.tokenB))
      )

      val cancelResF = actors.get(OrderHandlerActor.name) ? cancelReq

      val cancelRes = Await.result(cancelResF, timeout.duration)
      info(s"submit res: ${cancelRes}")

      Thread.sleep(1000)

      val getOrderFromDbF = dbModule.orderService.getOrder(rawOrder.hash)
      val getOrderFromDb =
        Await.result(getOrderFromDbF.mapTo[Option[XRawOrder]], timeout.duration)

      getOrderFromDb map { o =>
        o.getState.status should be(XOrderStatus.STATUS_CANCELLED_BY_USER)
      }

      actors.get(OrderbookManagerActor.name) ! XGetOrderbook(
        0,
        100,
        Some(XMarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )

      expectMsgPF() {
        case a: XOrderbook =>
          info("----orderbook status after cancel this order: " + a)
          assert(a.sells.isEmpty)
      }
    }

  }

}
