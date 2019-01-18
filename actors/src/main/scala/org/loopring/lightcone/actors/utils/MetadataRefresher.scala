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
      metadataManager: MetadataManager
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
    val metadataManager: MetadataManager)
    extends InitializationRetryActor
    with Stash
    with ActorLogging {
  val metadataManagerActor = actors.get(MetadataManagerActor.name)

  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(MetadataManagerActor.pubsubTopic, self)

  private var tokens = Seq.empty[TokenMetadata]
  private var markets = Seq.empty[MarketMetadata]

  override def initialize() =
    for {
      _ <- refreshMetadata()
    } yield becomeReady()

  def ready: Receive = {
    case _: MetadataChanged =>
      for {
        _ <- refreshMetadata()
      } yield Unit

    case _: GetMetadatas.Req =>
      sender ! GetMetadatas.Res(tokens = tokens, markets = markets)
  }

  private def refreshMetadata() =
    for {
      tokens_ <- (metadataManagerActor ? LoadTokenMetadata.Req())
        .mapTo[LoadTokenMetadata.Res]
        .map(_.tokens)
      markets_ <- (metadataManagerActor ? LoadMarketMetadata.Req())
        .mapTo[LoadMarketMetadata.Res]
        .map(_.markets)
    } yield {
      assert(tokens_.nonEmpty)
      assert(markets_.nonEmpty)
      tokens = tokens_
      markets = markets_
      metadataManager.reset(tokens_, markets_)
    }
}
