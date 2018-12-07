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

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{ Actor, ActorLogging, ActorRef, AllForOneStrategy, Props }
import akka.util.Timeout
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.persistence.OrdersDalActor
import org.loopring.lightcone.core.base.DustOrderEvaluator
import org.loopring.lightcone.proto.actors._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object AccountManagerShardingActor {
  val name = "account_manager_sharding"
}

/** AccountManager分片，
 *  由该actor启动子actor
 */
class AccountManagerShardingActor(
    addressPrefix: String
)(
    implicit
    val ec: ExecutionContext,
    val timeout: Timeout,
    val dustEvaluator: DustOrderEvaluator,
    actors: Lookup[ActorRef]
)
  extends Actor
  with ActorLogging {

  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 second) {
      case _: Exception ⇒ Restart //shardingActor对所有的异常都会重启自己，根据策略，也会重启下属所有的Actor
    }

  val accountManagerActors = new MapBasedLookup[ActorRef]()

  val ordersRecoveryActor = actors.get(OrdersRecoveryActor.name)
  val ordersDalActor = actors.get(OrdersDalActor.name)

  //发送恢复请求
  ordersRecoveryActor ! XRecoverOrdersReq()

  def receive: Receive = {
    case XAccountMsgWrap(address, data) ⇒
      if (!address.startsWith(addressPrefix)) {
        log.info(s"receive wrong msg: $address, $data")
      } else {
        val actorName = AccountManagerActor.name + address
        if (!actors.contains("")) {
          val newAccountActor = context.actorOf(Props(new AccountManagerActor(actors)), actorName)
          actors.add(actorName, newAccountActor)
        }
        //转发msg到AccountManager
        data match {
          case XAccountMsgWrap.Data.SubmitOrder(value) ⇒ actors.get(actorName) forward value
          case XAccountMsgWrap.Data.BalanceAndAllowancesReq(value) ⇒ actors.get(actorName) forward value
          case XAccountMsgWrap.Data.CancelOrder(value) ⇒ actors.get(actorName) forward value
          case _ ⇒
        }
      }
  }
}
