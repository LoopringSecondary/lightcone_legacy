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
import org.loopring.lightcone.proto.core.XOrderStatus
import org.loopring.lightcone.core.data.Order
import akka.pattern._
import akka.testkit.{ EventFilter, TestProbe }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import XErrorCode._

class CoreActorsIntegrationSpec_Simple
  extends CoreActorsIntegrationCommonSpec {

  def setBalanceActorReply(order: XOrder): Future[Unit] = for {
    _ ← Future.successful()
  } yield {
    accountBalanceProbe.expectQuery(ADDRESS_1, WETH)
    accountBalanceProbe.replyWith(WETH, BigInt("1000000"), BigInt("2000000"))

    accountBalanceProbe.expectQuery(ADDRESS_1, LRC)
    accountBalanceProbe.replyWith(LRC, BigInt("500000"), BigInt("300000"))

    orderHistoryProbe.expectQuery(order.id)
    orderHistoryProbe.replyWith(order.id, BigInt("0"))
  }

  "submitOrder to accountManager" must {
    "succeed if the trader has sufficient balance and allowance" in {
      val order = XOrder(
        id = "order",
        tokenS = WETH,
        tokenB = LRC,
        tokenFee = LRC,
        amountS = BigInt(100),
        amountB = BigInt(10),
        amountFee = BigInt(10),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.NEW
      )

      accountManagerActor1 ! XSubmitOrderReq(Some(order))

      accountBalanceProbe.expectQuery(ADDRESS_1, WETH)
      accountBalanceProbe.replyWith(WETH, BigInt("1000000"), BigInt("2000000"))

      accountBalanceProbe.expectQuery(ADDRESS_1, LRC)
      accountBalanceProbe.replyWith(LRC, BigInt("500000"), BigInt("300000"))

      orderHistoryProbe.expectQuery(order.id)
      orderHistoryProbe.replyWith(order.id, BigInt("0"))

      expectMsgPF() {
        case XSubmitOrderRes(ERR_OK, Some(xorder)) ⇒
          val order: Order = xorder
          log.info(s"order submitted: $order")
      }
    }
  }

  "submit two orders in the same time" must {
    "success if two order has been processed in the same time" in {
      val order = XOrder(
        id = "order",
        tokenS = WETH,
        tokenB = LRC,
        tokenFee = LRC,
        amountS = BigInt(100),
        amountB = BigInt(10),
        amountFee = BigInt(10),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.NEW
      )

      setBalanceActorReply(order)
      setBalanceActorReply(order)
      EventFilter.info(pattern = s"getTokenManager $WETH", occurrences = 2) intercept {
        accountManagerActor2 ! XSubmitOrderReq(Some(order))
        accountManagerActor2 ! XSubmitOrderReq(Some(order))
      }

    }
  }

  "submitOrder to marketManagerActor" must {

    "generate a ring, send it to settlementActor and orderbookManager receive two messages" in {
      var decimal = "000000000000000000000000000000".substring(0, 18)
      val maker1 = XOrder(
        id = "maker1",
        tokenS = WETH,
        tokenB = LRC,
        tokenFee = LRC,
        amountS = BigInt("10" + decimal),
        amountB = BigInt("100" + decimal),
        amountFee = BigInt("10" + decimal),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.NEW,
        actual = Some(XOrderState(BigInt("10" + decimal), BigInt("100" + decimal), BigInt("10" + decimal))),
        matchable = Some(XOrderState(BigInt("10" + decimal), BigInt("100" + decimal), BigInt("10" + decimal)))
      )

      val taker1 = XOrder(
        id = "taker1",
        tokenS = LRC,
        tokenB = WETH,
        tokenFee = LRC,
        amountS = BigInt("100" + decimal),
        amountB = BigInt("10" + decimal),
        amountFee = BigInt("10" + decimal),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.NEW,
        actual = Some(XOrderState(BigInt("100" + decimal), BigInt("10" + decimal), BigInt("10" + decimal))),
        matchable = Some(XOrderState(BigInt("100" + decimal), BigInt("10" + decimal), BigInt("10" + decimal)))
      )

      val orderbookPpobe = TestProbe()
      orderbookPpobe watch orderbookManagerActor
      val settlementProbe = TestProbe()
      settlementProbe watch settlementActor

      marketManagerActor ! XSubmitOrderReq(Some(maker1))
      orderbookPpobe.receiveOne(1 seconds)
      marketManagerActor ! XSubmitOrderReq(Some(taker1))
      orderbookPpobe.receiveOne(1 seconds)
      settlementProbe.receiveOne(1 seconds)
      ethereumProbe.receiveOne(1 seconds)
      //      val maker1Res = Await.result(maker1Future.mapTo[XSubmitOrderRes], timeout.duration)
      //      val taker1Res = Await.result(taker1Future.mapTo[XSubmitOrderRes], timeout.duration)
      //      info(maker1Res.error.toString())
      //      info(taker1Res.error.toString())
      Thread.sleep(10000)
    }

  }
}
