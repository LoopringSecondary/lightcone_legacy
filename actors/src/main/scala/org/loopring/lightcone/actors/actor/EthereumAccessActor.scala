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

package org.loopring.lightcone.actors.actor

import akka.actor._
import akka.event.LoggingReceive
import akka.util.Timeout
import org.loopring.lightcone.proto.actors.{ BalanceAndAllowance, GetBalanceAndAllowancesReq, GetBalanceAndAllowancesRes }
import org.loopring.lightcone.proto.deployment.EthereumAccessorSettings
import org.loopring.lightcone.actors.base

import scala.concurrent.ExecutionContext

object EthereumAccessActor
  extends base.Deployable[EthereumAccessorSettings] {
  val name = "ethereum_access_actor"

  def getCommon(s: EthereumAccessorSettings) =
    base.CommonSettings(None, s.roles, s.instances)
}

class EthereumAccessActor()(
    implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor
  with ActorLogging {

  def receive() = LoggingReceive {
    case GetBalanceAndAllowancesReq(address, tokens) ⇒
      val map_ = tokens.map { token ⇒
        // TODO: query Etherem and get the results.
        val balance: BigInt = 0;
        val allowance: BigInt = 0;
        token -> BalanceAndAllowance(balance, allowance)
      }.toMap

      sender ! GetBalanceAndAllowancesRes(address, map_)
  }
}
