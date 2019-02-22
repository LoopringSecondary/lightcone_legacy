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

package io.lightcone.relayer.actors

import akka.actor._
import akka.util.Timeout
import io.lightcone.core._
import io.lightcone.relayer.base._
import io.lightcone.ethereum.event._
import io.lightcone.core._
import io.lightcone.relayer.data.GetBalanceAndAllowances
import org.slf4s.Logging

import scala.concurrent._

class RecoverAccountsActor(
  )(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    actors: Lookup[ActorRef])
    extends Actor
    with Logging {

  val mama = actors.get(MultiAccountManagerActor.name)
  val query = actors.get(EthereumQueryActor.name)

  def receive = {
    case ChainReorganizationImpact(_, accounts) =>
      log.debug(s"started recovering accounts [size=${accounts.size}]")
      accounts.foreach {
        case ChainReorganizationImpact.AccountInfo(address, tokens) =>
          (query ? GetBalanceAndAllowances.Req(address, tokens))
            .mapAs[GetBalanceAndAllowances.Res]
            .map { resp =>
              resp.balanceAndAllowanceMap.foreach {
                case (token, ba) =>
                  mama ! AddressBalanceAllowanceUpdatedEvent(
                    address = address,
                    token = token,
                    balance = ba.balance,
                    allowance = ba.allowance,
                    blockNum = ba.blockNum
                  )
                case _ =>
              }
            }
      }
      context.stop(self)
      log.debug("finished recovering accounts")
  }
}
