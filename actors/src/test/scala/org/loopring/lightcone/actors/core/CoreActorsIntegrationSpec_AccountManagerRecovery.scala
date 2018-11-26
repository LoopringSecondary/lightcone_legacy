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

import akka.actor.{ Actor, ActorLogging }
import akka.event.LoggingReceive
import akka.testkit.TestActorRef
import akka.util.Timeout
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode.{ ERR_OK, ERR_UNKNOWN }
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import CoreActorsIntegrationCommonSpec._
import org.loopring.lightcone.proto.deployment.XStart

import scala.concurrent.ExecutionContext

class CoreActorsIntegrationSpec_AccountManagerRecovery
  extends CoreActorsIntegrationCommonSpec(XMarketId(GTO_TOKEN.address, WETH_TOKEN.address)) {

  class AccountBalanceForTestActor()(
      implicit
      ec: ExecutionContext,
      timeout: Timeout
  )
    extends Actor
    with ActorLogging {

    def receive: Receive = LoggingReceive {
      case req: XGetBalanceAndAllowancesReq ⇒
        sender !
          XGetBalanceAndAllowancesRes(
            req.address,
            Map(req.tokens(0) -> XBalanceAndAllowance(BigInt("1000000000000000000000000"), BigInt("1000000000000000000000000")))
          )

    }
  }
  val accountBalanceActor3 = TestActorRef(new AccountBalanceForTestActor())

  "when an accountManager starts" must {
    "first recover it and then receive order" in {
      val ADDRESS_3 = "0xaddress_3"
      actors.add(AccountBalanceActor.name, accountBalanceActor3)
      val accountManagerActor3 = TestActorRef(
        new AccountManagerActor(
          actors,
          address = ADDRESS_3,
          recoverBatchSize = 1,
          skipRecovery = false
        ),
        "accountManagerActor3"
      )
      val order = XRawOrder(
        hash = "order",
        tokenS = WETH_TOKEN.address,
        tokenB = GTO_TOKEN.address,
        feeToken = GTO_TOKEN.address,
        amountS = "50".zeros(18),
        amountB = "10000".zeros(18),
        feeAmount = "10".zeros(18),
        walletSplitPercentage = 100
      )

      accountManagerActor3 ! XStart

      var orderIds = (0 to 6) map ("order" + _)
      orderDatabaseAccessProbe.expectQuery()
      orderDatabaseAccessProbe.replyWith(Seq(
        order.copy(hash = orderIds(0))
      ))

      //      accountBalanceProbe.expectQuery(ADDRESS_3, WETH_TOKEN.address)
      //      accountBalanceProbe.replyWith(ADDRESS_3, WETH_TOKEN.address, "1000".zeros(18), "1000".zeros(18))
      //
      //      accountBalanceProbe.expectQuery(ADDRESS_3, GTO_TOKEN.address)
      //      accountBalanceProbe.replyWith(ADDRESS_3, GTO_TOKEN.address, "100".zeros(18), "100".zeros(18))

      orderHistoryProbe.expectQuery(orderIds(0))
      orderHistoryProbe.replyWith(orderIds(0), "0".zeros(0))

      Thread.sleep(1000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after first XRecoverOrdersRes: " + a)
      }

      orderDatabaseAccessProbe.expectQuery()
      orderDatabaseAccessProbe.replyWith(Seq(
        order.copy(hash = orderIds(1))
      ))

      orderHistoryProbe.expectQuery(orderIds(1))
      orderHistoryProbe.replyWith(orderIds(1), "0".zeros(0))

      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after second XRecoverOrdersRes: " + a)
      }
      orderDatabaseAccessProbe.expectQuery()
      orderDatabaseAccessProbe.replyWith(Seq())

      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after last XRecoverOrdersRes: " + a)
      }

      accountManagerActor3 ! XSubmitOrderReq(Some(order))

      orderHistoryProbe.expectQuery(order.hash)
      orderHistoryProbe.replyWith(order.hash, "0".zeros(0))

      expectMsgPF() {
        case XSubmitOrderRes(ERR_OK, Some(xorder)) ⇒
          val order: Order = xorder
          info(s"submitted an order: $order")
        case XSubmitOrderRes(ERR_UNKNOWN, None) ⇒
          info(s"occurs ERR_UNKNOWN when submitting order:$order")
      }

      Thread.sleep(1000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after submit an order: " + a)
      }

    }
  }

}
