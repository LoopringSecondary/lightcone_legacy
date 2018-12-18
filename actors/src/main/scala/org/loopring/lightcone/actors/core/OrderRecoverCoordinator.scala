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
import akka.actor._
import akka.cluster.sharding._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.base.DustOrderEvaluator
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto.XErrorCode._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object OrderRecoverCoordinator extends {
  val name = "order_recover_coordinator"

}

class OrderRecoverCoordinator(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dustEvaluator: DustOrderEvaluator)
    extends Actor
    with ActorLogging {

  def receive: Receive = {
    case req: Any =>
  }

}
