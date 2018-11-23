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

package org.loopring.lightcone.actors.deployment

import akka.actor._
import akka.util.Timeout
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.proto.actors._

import scala.concurrent.ExecutionContext

case class Shard(index: Int, totalShards: Int)

object AccountManagerActorDeployer {
  val name = "ama_deployer"
}

// TODO(dongw): Leave this to me to implement.
class AccountManagerActorDeployer(shard: Shard)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor
  with ActorLogging {

  private var actors = Map.empty[String, ActorRef]

  def receive: Receive = {
    case msg: AnyRef ⇒

  }
}
