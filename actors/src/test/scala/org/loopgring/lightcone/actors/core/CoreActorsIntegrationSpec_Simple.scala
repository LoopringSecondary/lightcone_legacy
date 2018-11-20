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
import akka.pattern._
import akka.testkit.TestProbe

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class CoreActorsIntegrationSpec_Simple
  extends CoreActorsIntegrationCommonSpec {

  def setBalanceActorReply(req: Any): Future[Unit] = for {
    _ ← Future.successful()
  } yield {
    accountBalanceProbe.expectUpdate(req)
  }

  "submitOrder to accountManagerActor" must {

    "send Order to marketManagerActor" in {

      setBalanceActorReply()
      setBalanceActorReply()
      setBalanceActorReply()
      setBalanceActorReply()
      setBalanceActorReply()
      setBalanceActorReply()
      val maker1 = XOrder(
        id = "maker1",
        tokenS = WETH,
        tokenB = LRC,
        tokenFee = LRC,
        amountS = BigInt(100),
        amountB = BigInt(10),
        amountFee = BigInt(10),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.NEW
      )
      val taker1 = XOrder(
        id = "taker1",
        tokenS = LRC,
        tokenB = WETH,
        tokenFee = LRC,
        amountS = BigInt(10),
        amountB = BigInt(100),
        amountFee = BigInt(10),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.NEW
      )

      accountManagerActor ! XSubmitOrderReq(Some(maker1))
      accountManagerActor ! XSubmitOrderReq(Some(taker1))
      Thread.sleep(10000)
    }

  }
  "submitOrder to marketManagerActor" must {

    "create ring" in {
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

      val maker1Future = marketManagerActor ? XSubmitOrderReq(Some(maker1))
      val taker1Future = marketManagerActor ? XSubmitOrderReq(Some(taker1))
      val maker1Res = Await.result(maker1Future.mapTo[XSubmitOrderRes], timeout.duration)
      val taker1Res = Await.result(taker1Future.mapTo[XSubmitOrderRes], timeout.duration)
      info(maker1Res.error.toString())
      info(taker1Res.error.toString())
      val orderbookPpobe = TestProbe()
      orderbookPpobe watch orderbookManagerActor
      orderbookPpobe.receiveOne(1 seconds)
      Thread.sleep(10000)
    }

  }
}
