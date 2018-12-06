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

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.util.Timeout
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.proto.actors.XHeatbeat

import scala.concurrent.ExecutionContext

object AccountManagerMonitorActor {
  val name = "account_manager_monitor"
}

/** 监控accountmanager的状态，如果已经死掉，需要由该actor重新生成actor
 */
class AccountManagerMonitorActor()(
    implicit
    val ec: ExecutionContext,
    val timeout: Timeout,
    actors: Lookup[ActorRef]
)
  extends Actor
  with ActorLogging {

  def receive: Receive = {
    case req: XHeatbeat ⇒
  }

}
