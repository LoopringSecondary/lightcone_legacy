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

package org.loopring.lightcone.actors.support

import akka.actor.{Actor, ActorLogging, Props}
import akka.testkit.TestProbe
import akka.util.Timeout
import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.core.EthereumQueryActor
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.ethereum.EthereumAccessActor

import scala.concurrent.ExecutionContext

trait EthereumQueryMockSupport {
  my: CommonSpec =>

  class EthereumAccessMockActor(
    )(
      implicit ec: ExecutionContext,
      timeout: Timeout)
      extends Actor
      with ActorLogging {

    def receive: Receive = {
      case req: GetNonce.Req => sender ! GetNonce.Res(result = "0x1")
      case msg =>
        log.debug(s"${EthereumAccessActor.name} receive msg: ${msg}")
    }
  }

  class EthereumQueryForRecoveryMockActor(
    )(
      implicit ec: ExecutionContext,
      timeout: Timeout)
      extends Actor
      with ActorLogging {

    var balance = BigInt("1000000000000000000000000000")
    var allowance = BigInt("1000000000000000000000000000")

    def receive: Receive = {
      case req: GetBalanceAndAllowances.Req =>
        sender !
          GetBalanceAndAllowances.Res(
            req.address,
            req.tokens.map { t =>
              (
                t,
                BalanceAndAllowance(
                  ByteString.copyFrom(balance.toByteArray),
                  ByteString.copyFrom(allowance.toByteArray)
                )
              )
            }.toMap
          )

      case GetFilledAmount.Req(orderIds) =>
        sender ! GetFilledAmount.Res(
          orderIds.map(id => id -> ByteString.copyFrom("0", "UTF-8")).toMap
        )

      case res: GetBalanceAndAllowances.Res =>
        res.balanceAndAllowanceMap.values.headOption map {
          case head =>
            balance = head.balance
            allowance = head.allowance
        }
        sender ! res
    }
  }

  val ethereumQueryActor =
    system.actorOf(Props(new EthereumQueryForRecoveryMockActor()))
  actors.del(EthereumQueryActor.name)
  actors.add(EthereumQueryActor.name, ethereumQueryActor)

  val ethereumAccessActor =
    system.actorOf(Props(new EthereumAccessMockActor()))
  actors.del(EthereumAccessActor.name)
  actors.add(EthereumAccessActor.name, ethereumAccessActor)
}
