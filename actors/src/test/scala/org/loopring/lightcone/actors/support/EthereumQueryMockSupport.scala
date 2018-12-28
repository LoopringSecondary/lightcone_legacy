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
import org.loopring.lightcone.actors.core.{
  EthereumQueryActor,
  MarketManagerActor
}
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.data._
import scala.concurrent.ExecutionContext

trait EthereumQueryMockSupport {
  my: CommonSpec =>

  class EthereumQueryForRecoveryMockActor(
    )(
      implicit ec: ExecutionContext,
      timeout: Timeout)
      extends Actor
      with ActorLogging {

    var balance = BigInt("1000000000000000000000000000")
    var allowance = BigInt("1000000000000000000000000000")

    def receive: Receive = {
      case req: XGetBalanceAndAllowancesReq =>
        sender !
          XGetBalanceAndAllowancesRes(
            req.address,
            Map(
              req.tokens(0) -> XBalanceAndAllowance(
                ByteString.copyFrom(
                  balance.toByteArray
                ),
                ByteString.copyFrom(
                  allowance.toByteArray
                )
              )
            )
          )

      case XGetFilledAmountReq(orderIds) =>
        sender ! XGetFilledAmountRes(
          orderIds.map(id ⇒ id → ByteString.copyFrom("0", "UTF-8")).toMap
        )

      case res: XGetBalanceAndAllowancesRes =>
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
}
