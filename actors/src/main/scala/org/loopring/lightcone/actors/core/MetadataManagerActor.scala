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
import akka.pattern._
import org.loopring.lightcone.actors.base.safefuture._

// Owner: Yongfeng
object MetadataManagerActor {
  val name = "metadata_manager"
  val pubsubTopic = "TOKEN_MARKET_METADATA_CHANGE"

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
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-dalay-in-seconds")

  val mediator = DistributedPubSub(context.system).mediator
  val ethereumQueryActor = actors.get(EthereumQueryActor.name)

  private var tokens: Set[TokenMetadata] = Set.empty
  private var markets: Set[MarketMetadata] = Set.empty

  override def initialize() =
    for {
      tokens_ <- dbModule.tokenMetadataDal.getTokens()
      markets_ <- dbModule.marketMetadataDal.getMarkets()
      tokensUpdated <- Future.sequence(tokens_.map { token =>
        for {
          burnRateRes <- (ethereumQueryActor ? GetBurnRate.Req(
            token = token.address
          )).mapTo[GetBurnRate.Res]
          _ <- if (token.burnRate != burnRateRes.burnRate)
            dbModule.tokenMetadataDal
              .updateBurnRate(token.address, burnRateRes.burnRate)
          else Future.successful(Unit)
        } yield token.copy(burnRate = burnRateRes.burnRate)
      })
    } yield {
      tokens = tokensUpdated.toSet
      markets = markets_.toSet
      becomeReady()
    }

  val repeatedJobs = Seq(
    Job(
      name = "load_tokens_markets_metadata",
      dalayInSeconds = refreshIntervalInSeconds,
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => syncConfigs()
    )
  )

  def ready: Receive = super.receiveRepeatdJobs orElse {

    case req: SaveTokenMetadatas.Req =>
      (for {
        saved <- dbModule.tokenMetadataDal
          .saveTokens(req.tokens)
        tokens_ <- dbModule.tokenMetadataDal.getTokens()
      } yield {
        if (saved.nonEmpty) {
          tokens = tokens_.toSet
          mediator ! Publish(
            MetadataManagerActor.pubsubTopic,
            MetadataChanged()
          )
        }
        SaveTokenMetadatas.Res(saved)
      }).sendTo(sender)

    case req: UpdateTokenMetadata.Req =>
      (for {
        burnRateRes <- (ethereumQueryActor ? GetBurnRate.Req(
          token = req.token.get.address
        )).mapTo[GetBurnRate.Res]
        updated <- dbModule.tokenMetadataDal
          .updateToken(req.token.get.copy(burnRate = burnRateRes.burnRate))
        tokens_ <- dbModule.tokenMetadataDal.getTokens()
      } yield {
        if (updated == ErrorCode.ERR_NONE) {
          tokens = tokens_.toSet
          mediator ! Publish(
            MetadataManagerActor.pubsubTopic,
            MetadataChanged()
          )
        }
        UpdateTokenMetadata.Res(updated)
      }).sendTo(sender)

    case req: UpdateTokenBurnRate.Req =>
      (for {
        burnRateRes <- (ethereumQueryActor ? GetBurnRate.Req(
          token = req.address
        )).mapTo[GetBurnRate.Res]
        updated <- dbModule.tokenMetadataDal
          .updateBurnRate(req.address, burnRateRes.burnRate)
        tokens_ <- dbModule.tokenMetadataDal.getTokens()
      } yield {
        if (updated == ErrorCode.ERR_NONE) {
          tokens = tokens_.toSet
          mediator ! Publish(
            MetadataManagerActor.pubsubTopic,
            MetadataChanged()
          )
        }
        UpdateTokenBurnRate.Res(updated)
      }).sendTo(sender)

    case req: DisableToken.Req =>
      (for {
        updated <- dbModule.tokenMetadataDal
          .disableToken(req.address)
        tokens_ <- dbModule.tokenMetadataDal.getTokens()
      } yield {
        if (updated == ErrorCode.ERR_NONE) {
          tokens = tokens_.toSet
          mediator ! Publish(
            MetadataManagerActor.pubsubTopic,
            MetadataChanged()
          )
        }
        DisableToken.Res(updated)
      }).sendTo(sender)

    case req: SaveMarketMetadatas.Req =>
      (for {
        saved <- dbModule.marketMetadataDal
          .saveMarkets(req.markets)
        markets_ <- dbModule.marketMetadataDal.getMarkets()
      } yield {
        if (saved.nonEmpty) {
          markets = markets_.toSet
          mediator ! Publish(
            MetadataManagerActor.pubsubTopic,
            MetadataChanged()
          )
        }
        SaveMarketMetadatas.Res(saved)
      }).sendTo(sender)

    case req: UpdateMarketMetadata.Req =>
      (for {
        updated <- dbModule.marketMetadataDal
          .updateMarket(req.market.get)
        markets_ <- dbModule.marketMetadataDal.getMarkets()
      } yield {
        if (updated == ErrorCode.ERR_NONE) {
          markets = markets_.toSet
          mediator ! Publish(
            MetadataManagerActor.pubsubTopic,
            MetadataChanged()
          )
        }
        UpdateMarketMetadata.Res(updated)
      }).sendTo(sender)

    case req: DisableMarket.Req =>
      (for {
        disabled <- dbModule.marketMetadataDal
          .disableMarketByHash(req.marketHash)
        markets_ <- dbModule.marketMetadataDal.getMarkets()
      } yield {
        if (disabled == ErrorCode.ERR_NONE) {
          markets = markets_.toSet
          mediator ! Publish(
            MetadataManagerActor.pubsubTopic,
            MetadataChanged()
          )
        }
        DisableMarket.Res(disabled)
      }).sendTo(sender)

    case req: LoadTokenMetadata.Req =>
      Future.successful(LoadTokenMetadata.Res(tokens.toSeq)).sendTo(sender)

    case req: LoadMarketMetadata.Req =>
      Future.successful(LoadMarketMetadata.Res(markets.toSeq)).sendTo(sender)
  }

  private def syncConfigs(): Future[Unit] = {
    log.info("MetadataManagerActor run tokens and markets reload job")
    for {
      loadTokens <- dbModule.tokenMetadataDal.getTokens()
      loadMarkets <- dbModule.marketMetadataDal.getMarkets()
    } yield {
      val tokensSet = loadTokens.toSet
      val marketsSet = loadMarkets.toSet
      if (tokensSet.diff(tokens).nonEmpty || tokens
            .diff(tokensSet)
            .nonEmpty || marketsSet.diff(markets).nonEmpty || markets
            .diff(marketsSet)
            .nonEmpty)
        mediator ! Publish(
          MetadataManagerActor.pubsubTopic,
          MetadataChanged()
        )
      tokens = tokensSet
      markets = marketsSet
    }
  }
}
