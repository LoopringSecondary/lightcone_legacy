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

import akka.actor._
import akka.cluster.singleton._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Matchable
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto.OrderStatus._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._
import scala.concurrent._

// Owner: Yadong
object EthereumEventExtractorActor {
  val name = "ethereum_event_extractor"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new EthereumEventExtractorActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      name = EthereumEventExtractorActor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${EthereumEventExtractorActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${EthereumEventExtractorActor.name}_proxy"
    )

  }
}

class EthereumEventExtractorActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends ActorWithPathBasedConfig(EthereumEventExtractorActor.name) {

  def ready: Receive = {
    case _ =>
  }

}
