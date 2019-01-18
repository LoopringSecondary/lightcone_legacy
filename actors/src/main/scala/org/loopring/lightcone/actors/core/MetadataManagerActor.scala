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
import org.loopring.lightcone.proto.TxStatus.TX_STATUS_SUCCESS

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

  private var tokens = Seq.empty[TokenMetadata]
  private var markets = Seq.empty[MarketMetadata]

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
      tokens = tokensUpdated
      markets = markets_
      mediator ! Publish(MetadataManagerActor.pubsubTopic, MetadataChanged())
      becomeReady()
    }

  val repeatedJobs = Seq(
    Job(
      name = "load_tokens_markets_metadata",
      dalayInSeconds = refreshIntervalInSeconds,
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => syncMetadata()
    )
  )

  def ready: Receive = super.receiveRepeatdJobs orElse {

    case req: SaveTokenMetadatas.Req =>
      (for {
        saved <- dbModule.tokenMetadataDal.saveTokens(req.tokens)
        tokens_ <- dbModule.tokenMetadataDal.getTokens()
      } yield {
        if (saved.nonEmpty) {
          checkAndPublish(Some(tokens_), None)
        }
        SaveTokenMetadatas.Res(saved)
      }).sendTo(sender)

    case req: UpdateTokenMetadata.Req =>
      (for {
        burnRateRes <- (ethereumQueryActor ? GetBurnRate.Req(
          token = req.token.get.address
        )).mapTo[GetBurnRate.Res]
        result <- dbModule.tokenMetadataDal
          .updateToken(req.token.get.copy(burnRate = burnRateRes.burnRate))
        tokens_ <- dbModule.tokenMetadataDal.getTokens()
      } yield {
        if (result == ErrorCode.ERR_NONE) {
          checkAndPublish(Some(tokens_), None)
        }
        UpdateTokenMetadata.Res(result)
      }).sendTo(sender)

    case req: TokenBurnRateChangedEvent =>
      if (req.header.nonEmpty && req.getHeader.txStatus.isTxStatusSuccess) {
        (for {
          burnRateRes <- (ethereumQueryActor ? GetBurnRate.Req(
            token = req.token
          )).mapTo[GetBurnRate.Res]
          result <- dbModule.tokenMetadataDal
            .updateBurnRate(req.token, burnRateRes.burnRate)
          tokens_ <- dbModule.tokenMetadataDal.getTokens()
        } yield {
          if (result == ErrorCode.ERR_NONE) {
            checkAndPublish(Some(tokens_), None)
          }
          UpdateTokenBurnRate.Res(result)
        }).sendTo(sender)
      }

    case req: DisableToken.Req =>
      (for {
        result <- dbModule.tokenMetadataDal
          .disableToken(req.address)
        tokens_ <- dbModule.tokenMetadataDal.getTokens()
      } yield {
        if (result == ErrorCode.ERR_NONE) {
          checkAndPublish(Some(tokens_), None)
        }
        DisableToken.Res(result)
      }).sendTo(sender)

    case req: SaveMarketMetadatas.Req =>
      (for {
        saved <- dbModule.marketMetadataDal
          .saveMarkets(req.markets)
        markets_ <- dbModule.marketMetadataDal.getMarkets()
      } yield {
        if (saved.nonEmpty) {
          checkAndPublish(None, Some(markets_))
        }
        SaveMarketMetadatas.Res(saved)
      }).sendTo(sender)

    case req: UpdateMarketMetadata.Req =>
      (for {
        result <- dbModule.marketMetadataDal
          .updateMarket(req.market.get)
        markets_ <- dbModule.marketMetadataDal.getMarkets()
      } yield {
        if (result == ErrorCode.ERR_NONE) {
          checkAndPublish(None, Some(markets_))
        }
        UpdateMarketMetadata.Res(result)
      }).sendTo(sender)

    case req: DisableMarket.Req =>
      (for {
        result <- dbModule.marketMetadataDal
          .disableMarketByHash(req.marketHash)
        markets_ <- dbModule.marketMetadataDal.getMarkets()
      } yield {
        if (result == ErrorCode.ERR_NONE) {
          checkAndPublish(None, Some(markets_))
        }
        DisableMarket.Res(result)
      }).sendTo(sender)

    case req: LoadTokenMetadata.Req =>
      sender ! LoadTokenMetadata.Res(tokens)

    case req: LoadMarketMetadata.Req =>
      sender ! LoadMarketMetadata.Res(markets)
  }

  private def publish() = {
    mediator ! Publish(MetadataManagerActor.pubsubTopic, MetadataChanged())
  }

  private def checkAndPublish(
      tokensOpt: Option[Seq[TokenMetadata]],
      marketsOpt: Option[Seq[MarketMetadata]]
    ) {
    var notify = false
    tokensOpt foreach { tokens_ =>
      if (tokens_ != tokens) {
        notify = true
        tokens = tokens_
      }
    }

    marketsOpt foreach { markets_ =>
      if (markets_ != markets) {
        notify = true
        markets = markets_
      }
    }

    if (notify) publish()
  }

  private def syncMetadata() = {
    log.info("MetadataManagerActor run tokens and markets reload job")
    for {
      tokens_ <- dbModule.tokenMetadataDal.getTokens()
      markets_ <- dbModule.marketMetadataDal.getMarkets()
    } yield {
      checkAndPublish(Some(tokens_), Some(markets_))
    }
  }
}
