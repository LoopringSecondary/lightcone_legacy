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

package org.loopgring.lightcone.actors.core

import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.core.data.Order
import akka.pattern._
import akka.testkit.{ EventFilter, TestProbe }
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent._
import XErrorCode._

class CoreActorsIntegrationSpec_Simple
  extends CoreActorsIntegrationCommonSpec {

  // "submitOrder to accountManager" must {
  //   "succeed if the trader has sufficient balance and allowance" in {
  //     val order = XOrder(
  //       id = "order",
  //       tokenS = WETH,
  //       tokenB = LRC,
  //       tokenFee = LRC,
  //       amountS = BigInt(100),
  //       amountB = BigInt(10),
  //       amountFee = BigInt(10),
  //       walletSplitPercentage = 0.2,
  //       status = XOrderStatus.NEW
  //     )

  //     accountManagerActor1 ! XSubmitOrderReq(Some(order))

  //     accountBalanceProbe.expectQuery(ADDRESS_1, WETH)
  //     accountBalanceProbe.replyWith(WETH, BigInt("1000000"), BigInt("2000000"))

  //     accountBalanceProbe.expectQuery(ADDRESS_1, LRC)
  //     accountBalanceProbe.replyWith(LRC, BigInt("500000"), BigInt("300000"))

  //     orderHistoryProbe.expectQuery(order.id)
  //     orderHistoryProbe.replyWith(order.id, BigInt("0"))

  //     expectMsgPF() {
  //       case XSubmitOrderRes(ERR_OK, Some(xorder)) ⇒
  //         val order: Order = xorder
  //         log.debug(s"order submitted: $order")
  //     }
  //   }
  // }

  "submitOrder to marketManagerActor" must {
    "generate a ring, send it to settlementActor and orderbookManager receive two messages" in {
      val maker = XOrder(
        id = "maker",
        tokenS = WETH,
        tokenB = GTO,
        tokenFee = LRC,
        amountS = "10".zeros(18),
        amountB = "100".zeros(10),
        amountFee = "10".zeros(18),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.NEW
      )

      // val taker = XOrder(
      //   id = "taker",
      //   tokenS = GTO,
      //   tokenB = WETH,
      //   tokenFee = LRC,
      //   amountS = BigInt("100".zeros(10)),
      //   amountB = BigInt("10".zeros(18)),
      //   amountFee = BigInt("10".zeros(18)),
      //   walletSplitPercentage = 0.2,
      //   status = XOrderStatus.NEW)

      accountManagerActor1 ! XSubmitOrderReq(Some(maker))
      accountBalanceProbe.expectQuery(ADDRESS_1, WETH)
      accountBalanceProbe.replyWith(WETH, "10".zeros(18), "10".zeros(18))

      accountBalanceProbe.expectQuery(ADDRESS_1, LRC)
      accountBalanceProbe.replyWith(LRC, "10".zeros(18), "10".zeros(18))

      orderHistoryProbe.expectQuery(maker.id)
      orderHistoryProbe.replyWith(maker.id, "0".zeros(0))

      expectMsgPF() {
        case XSubmitOrderRes(ERR_OK, Some(xorder)) ⇒
          val order: Order = xorder
          log.debug(s"order submitted: $order")
      }

      // Thread.sleep(3000)
      orderbookManagerActor ! XGetOrderbookReq(0, 100)

      expectMsgPF() {
        case x ⇒
          println(s"============= $x")
      }

      Thread.sleep(5000)

    }
  }
}
