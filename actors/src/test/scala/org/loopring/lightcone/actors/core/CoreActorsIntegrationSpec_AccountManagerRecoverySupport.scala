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
import org.loopring.lightcone.actors.persistence.{ OrdersDalActor }
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.actors._

import scala.concurrent.ExecutionContext

abstract class CoreActorsIntegrationSpec_AccountManagerRecoverySupport(marketId: XMarketId)
  extends CoreActorsIntegrationCommonSpec(marketId) {

  class OrderStateForRecoveryTestActor()(
      implicit
      ec: ExecutionContext,
      timeout: Timeout
  )
    extends Actor
    with ActorLogging {

    def receive: Receive = LoggingReceive {
      case XGetOrderFilledAmountReq(orderId) ⇒
        sender ! XGetOrderFilledAmountRes(orderId, BigInt(0))
    }
  }

  class AccountBalanceForRecoveryTestActor()(
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
            Map(req.tokens(0) -> XBalanceAndAllowance(
              BigInt("100000000000000000000000000"),
              BigInt("100000000000000000000000000")
            ))
          )
    }
  }

  val orderStateRecoveryActor = TestActorRef(new OrderStateForRecoveryTestActor())
  val accountBalanceRecoveryActor = TestActorRef(new AccountBalanceForRecoveryTestActor())
  val ADDRESS_RECOVERY = "0xaddress_3"
  actors.del(OrderHistoryActor.name)
  actors.del(AccountBalanceActor.name)
  actors.add(OrderHistoryActor.name, orderStateRecoveryActor)
  actors.add(AccountBalanceActor.name, accountBalanceRecoveryActor)

}
