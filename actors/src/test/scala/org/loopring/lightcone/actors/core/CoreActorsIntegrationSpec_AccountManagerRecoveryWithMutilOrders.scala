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
import org.loopring.lightcone.proto.deployment.XStart
import CoreActorsIntegrationCommonSpec._

import scala.concurrent.ExecutionContext

class CoreActorsIntegrationSpec_AccountManagerRecoveryWithMutilOrders
  extends CoreActorsIntegrationCommonSpec(XMarketId(GTO_TOKEN.address, WETH_TOKEN.address)) {

  class OrderHistoryForTestActor()(
      implicit
      ec: ExecutionContext,
      timeout: Timeout
  )
    extends Actor
    with ActorLogging {

    def receive: Receive = LoggingReceive {
      case XGetOrderFilledAmountReq(orderId) ⇒ //从数据库获取
        sender ! XGetOrderFilledAmountRes(orderId, BigInt(0))
    }
  }

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

  val ADDRESS_3 = "0xaddress_3"
  val accountManagerActor3 = TestActorRef(
    new AccountManagerActor(
      actors,
      address = ADDRESS_3,
      recoverBatchSize = 2,
      skipRecovery = false
    ), "accountManagerActor3"
  )
  val orderHistoryActor3 = TestActorRef(new OrderHistoryForTestActor())
  val accountBalanceActor3 = TestActorRef(new AccountBalanceForTestActor())
  val marketManagerActor3 = TestActorRef(new MarketManagerActor(
    actors,
    XMarketId(GTO_TOKEN.address, WETH_TOKEN.address),
    config,
    skipRecovery = true
  ))
  accountManagerActor3 ! XStart

  marketManagerActor3 ! XStart

  "when an accountManager starts" must {
    "first recover it and then receive order" in {

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

      println(s"${accountBalanceActor.path.toString}")
      accountManagerActor3 ! XStart()

      var orderIds = (0 to 6) map ("order" + _)
      orderDatabaseAccessProbe.expectQuery()
      orderDatabaseAccessProbe.replyWith(Seq(
        order.copy(hash = orderIds(0)),
        order.copy(hash = orderIds(1))
      ))

      Thread.sleep(1000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case a: XOrderbook ⇒
          info("----orderbook status after first XRecoverOrdersRes: " + a)
      }

      orderDatabaseAccessProbe.expectQuery()
      orderDatabaseAccessProbe.replyWith(Seq(
        order.copy(hash = orderIds(2)),
        order.copy(hash = orderIds(3))
      ))

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

      //不能立即发送请求，否则可能会失败，需要等待future执行完毕
      accountManagerActor3 ! XSubmitOrderReq(Some(order.copy(hash = "order---1")))

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
