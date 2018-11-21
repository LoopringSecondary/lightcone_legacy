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

import XErrorCode._

class CoreActorsIntegrationSpec_Simple
  extends CoreActorsIntegrationCommonSpec {

  "submitOrder to accountManagerActor1" must {
    "send Order to marketManagerActor" in {
      val order = XOrder(
        id = "order",
        tokenS = WETH,
        tokenB = LRC,
        tokenFee = LRC,
        amountS = BigInt(100),
        amountB = BigInt(10),
        amountFee = BigInt(10),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.NEW)

      accountManagerActor1 ! XSubmitOrderReq(Some(order))

      accountBalanceProbe.expectQuery(ADDRESS_1, WETH)
      accountBalanceProbe.replyWith(WETH, BigInt("1000000"), BigInt("2000000"))

      accountBalanceProbe.expectQuery(ADDRESS_1, LRC)
      accountBalanceProbe.replyWith(LRC, BigInt("500000"), BigInt("300000"))

      // gasPriceProb ? XGetGasPriceReq

      expectMsgPF() {
        case XSubmitOrderRes(ERR_OK, _) ⇒
      }
    }

  }
}
