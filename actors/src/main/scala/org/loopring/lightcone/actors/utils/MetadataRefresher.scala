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

package org.loopring.lightcone.actors.utils

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.core.MetadataManagerActor
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.proto._
import scala.concurrent._

// Owner: Hongyu
object MetadataRefresher {
  val name = "metadata_refresher"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      tokenManager: TokenManager
    ) = {
    system.actorOf(
      Props(new MetadataRefresher()),
      MetadataRefresher.name
    )
  }
}

// main owner: 杜永丰
class MetadataRefresher(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule,
    val tokenManager: TokenManager)
    extends InitializationRetryActor
    with Stash
    with ActorLogging {
  val metadataManagerActor = actors.get(MetadataManagerActor.name)

  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(MetadataManagerActor.pubsubTopic, self)

  override def initialize() =
    for {
      tokens <- (metadataManagerActor ? LoadTokenMetadata.Req())
        .mapTo[LoadTokenMetadata.Res]
        .map(_.tokens)
    } yield {
      tokenManager.reset(tokens)
      becomeReady()
    }

  def ready: Receive = {
    case req: MetadataChanged =>
      (metadataManagerActor ? LoadTokenMetadata.Req())
        .mapTo[LoadTokenMetadata.Res]
        .map { res =>
          tokenManager.reset(res.tokens)
        }
    //TODO(du): Market ?
  }
}
