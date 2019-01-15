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
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.singleton._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import scala.concurrent.{ExecutionContext, Future}

// Owner: Yongfeng
object MetadataManagerActor {
  val name = "metadata_manager"
  val tokenType = "tokens"
  val marketType = "markets"

  val marketChangedTopicId = MetadataManagerActor.name + "-" + marketType + "-changed"
  val tokenChangedTopicId = MetadataManagerActor.name + "-" + tokenType + "-changed"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      dbModule: DatabaseModule,
      actors: Lookup[ActorRef],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new MetadataManagerActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      MetadataManagerActor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${MetadataManagerActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${MetadataManagerActor.name}_proxy"
    )
  }
}

class MetadataManagerActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(MetadataManagerActor.name)
    with RepeatedJobActor
    with ActorLogging {

  val repeatedDelayInSeconds = selfConfig.getInt("delay-in-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-dalay-in-seconds")

  val mediator = DistributedPubSub(context.system).mediator

  val repeatedJobs = Seq(
    Job(
      name = "load_tokens_metadata",
      dalayInSeconds = repeatedDelayInSeconds, // 10 minute
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => runJob(MetadataManagerActor.tokenType)
    ),
    Job(
      name = "load_markets_metadata",
      dalayInSeconds = repeatedDelayInSeconds, // 10 minute
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => runJob(MetadataManagerActor.marketType)
    )
  )

  def ready: Receive = super.receiveRepeatdJobs orElse {

    case req: SaveTokenMetadatas.Req =>
      dbModule.tokenMetadataService
        .saveTokens(req.tokens)
        .map(SaveTokenMetadatas.Res(_))

    case req: UpdateTokenMetadata.Req =>
      dbModule.tokenMetadataService
        .updateToken(req.token.get)
        .map(UpdateTokenMetadata.Res(_))

    case req: UpdateTokenBurnRate.Req =>
      dbModule.tokenMetadataService
        .updateBurnRate(req.address, req.burnRate)
        .map(UpdateTokenBurnRate.Res(_))

    case req: DisableToken.Req =>
      dbModule.tokenMetadataService
        .disableToken(req.address)
        .map(DisableToken.Res(_))

    case req: SaveMarketMetadatas.Req =>
      dbModule.marketMetadataService
        .saveMarkets(req.markets)
        .map(SaveMarketMetadatas.Res(_))

    case req: UpdateMarketMetadata.Req =>
      dbModule.marketMetadataService
        .updateMarket(req.market.get)
        .map(UpdateMarketMetadata.Res(_))

    case req: DisableMarket.Req =>
      dbModule.marketMetadataService
        .disableMarketByHash(req.marketHash)
        .map(DisableMarket.Res(_))

    case req: LoadTokenMetadata.Req =>
      dbModule.tokenMetadataService
        .getTokens()
        .map(LoadTokenMetadata.Res(_))

    case req: LoadMarketMetadata.Req =>
      dbModule.marketMetadataService
        .getMarkets()
        .map(LoadMarketMetadata.Res(_))
  }

  private def runJob(jobType: String): Future[Unit] = {
    log.info(s"MetadataManager run job:$jobType")
    jobType match {
      case MetadataManagerActor.tokenType =>
        for {
          loadFromMemery <- dbModule.tokenMetadataService.getTokens()
          loadFromDb <- dbModule.tokenMetadataService.getTokens(true)
        } yield {
          val sortedMemResult = loadFromMemery.sortWith(sortTokenByAddress)
          val sortedDbResult = loadFromDb.sortWith(sortTokenByAddress)
          if (sortedDbResult.length != sortedMemResult.length || sortedDbResult != sortedMemResult)
            mediator ! Publish(
              MetadataManagerActor.tokenChangedTopicId,
              MetadataChanged(MetadataChanged.Changed.Tokens(true))
            )
        }

      case MetadataManagerActor.marketType =>
        for {
          loadFromMemery <- dbModule.marketMetadataService.getMarkets()
          loadFromDb <- dbModule.marketMetadataService.getMarkets(true)
        } yield {
          val sortedMemResult = loadFromMemery.sortWith(sortMarketByHash)
          val sortedDbResult = loadFromDb.sortWith(sortMarketByHash)
          if (sortedDbResult.length != sortedMemResult.length || sortedDbResult != sortedMemResult)
            mediator ! Publish(
              MetadataManagerActor.marketChangedTopicId,
              MetadataChanged(MetadataChanged.Changed.Markets(true))
            )
        }
    }
  }

  private def sortTokenByAddress(
      t1: TokenMetadata,
      t2: TokenMetadata
    ) = {
    Math.abs(t1.address.hashCode) > Math.abs(t2.address.hashCode)
  }

  private def sortMarketByHash(
      m1: MarketMetadata,
      m2: MarketMetadata
    ) = {
    Math.abs(m1.marketHash.hashCode) > Math.abs(m2.marketHash.hashCode)
  }
}
