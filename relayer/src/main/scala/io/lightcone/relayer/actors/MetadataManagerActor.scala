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

package io.lightcone.relayer.actors

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.ethereum.event._
import io.lightcone.relayer.base._
import io.lightcone.persistence._
import io.lightcone.core._
import io.lightcone.relayer.data._
import scala.concurrent.{ExecutionContext, Future}
import akka.pattern._
import scala.util._

// Owner: Yongfeng
object MetadataManagerActor extends DeployedAsSingleton {
  val name = "metadata_manager"
  val pubsubTopic = "TOKEN_MARKET_METADATA_CHANGE"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeout: Timeout,
      dbModule: DatabaseModule,
      actors: Lookup[ActorRef],
      metadataManager: MetadataManager,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new MetadataManagerActor()))
  }
}

class MetadataManagerActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val metadataManager: MetadataManager,
    val dbModule: DatabaseModule)
    extends InitializationRetryActor
    with RepeatedJobActor
    with ActorLogging {

  import ErrorCode._

  val selfConfig = config.getConfig(MetadataManagerActor.name)
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-dalay-in-seconds")

  val mediator = DistributedPubSub(context.system).mediator
  @inline def ethereumQueryActor = actors.get(EthereumQueryActor.name)

  private var tokenMetadatas = Seq.empty[TokenMetadata]
  private var tokenInfos = Seq.empty[TokenInfo]
  private var markets = Seq.empty[MarketMetadata]

  override def initialize() = {
    val f = for {
      tokenMetadatas_ <- dbModule.tokenMetadataDal.getTokens()
      // TODO(du) tokeninfos
      markets_ <- dbModule.marketMetadataDal.getMarkets()
      tokensUpdated <- Future.sequence(tokenMetadatas_.map { token =>
        for {
          burnRateRes <- (ethereumQueryActor ? GetBurnRate.Req(
            token = token.address
          )).mapTo[GetBurnRate.Res]
          burnRate = burnRateRes.getBurnRate
          _ <- if (token.burnRateForMarket != burnRate.forMarket || token.burnRateForP2P != burnRate.forP2P)
            dbModule.tokenMetadataDal
              .updateBurnRate(
                token.address,
                burnRate.forMarket,
                burnRate.forP2P
              )
          else Future.unit
        } yield
          token.copy(
            burnRateForMarket = burnRate.forMarket,
            burnRateForP2P = burnRate.forP2P
          )
      })
    } yield {
      assert(tokensUpdated nonEmpty)
      assert(markets_ nonEmpty)
      tokenMetadatas = tokensUpdated.map(MetadataManager.normalize)
      markets = markets_.map(MetadataManager.normalize)
      //TODO(du):tickers待cmc分支实现
      metadataManager.reset(tokenMetadatas, tokenInfos, Map.empty, markets)
    }
    f onComplete {
      case Success(_) =>
        mediator ! Publish(MetadataManagerActor.pubsubTopic, MetadataChanged())
        becomeReady()
      case Failure(e) =>
        throw e
    }
    f
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
        burnRate = burnRateRes.getBurnRate
        result <- dbModule.tokenMetadataDal
          .updateToken(
            req.token.get.copy(
              burnRateForMarket = burnRate.forMarket,
              burnRateForP2P = burnRate.forP2P
            )
          )
        tokens_ <- dbModule.tokenMetadataDal.getTokens()
      } yield {
        if (result == ERR_NONE) {
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
          burnRate = burnRateRes.getBurnRate
          result <- dbModule.tokenMetadataDal
            .updateBurnRate(
              req.token,
              burnRate.forMarket,
              burnRate.forP2P
            )
          tokens_ <- dbModule.tokenMetadataDal.getTokens()
        } yield {
          if (result == ERR_NONE) {
            checkAndPublish(Some(tokens_), None)
          }
          UpdateTokenBurnRate.Res(result)
        }).sendTo(sender)
      }

    case req: InvalidateToken.Req =>
      (for {
        result <- dbModule.tokenMetadataDal
          .invalidateToken(req.address)
        tokens_ <- dbModule.tokenMetadataDal.getTokens()
      } yield {
        if (result == ERR_NONE) {
          checkAndPublish(Some(tokens_), None)
        }
        InvalidateToken.Res(result)
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
        if (result == ERR_NONE) {
          checkAndPublish(None, Some(markets_))
        }
        UpdateMarketMetadata.Res(result)
      }).sendTo(sender)

    case req: TerminateMarket.Req =>
      (for {
        result <- dbModule.marketMetadataDal
          .terminateMarketByKey(req.marketHash)
        markets_ <- dbModule.marketMetadataDal.getMarkets()
      } yield {
        if (result == ERR_NONE) {
          checkAndPublish(None, Some(markets_))
        }
        TerminateMarket.Res(result)
      }).sendTo(sender)

    case req: LoadTokenMetadata.Req =>
      sender ! LoadTokenMetadata.Res(tokenMetadatas)

    case req: LoadMarketMetadata.Req =>
      sender ! LoadMarketMetadata.Res(markets)
  }

  private def publish() = {
    mediator ! Publish(MetadataManagerActor.pubsubTopic, MetadataChanged())
  }

  private def checkAndPublish(
      tokensOpt: Option[Seq[TokenMetadata]],
      marketsOpt: Option[Seq[MarketMetadata]]
    ): Unit = {
    var notify = false
    tokensOpt foreach { tokens_ =>
      if (tokens_ != tokenMetadatas) {
        notify = true
        tokenMetadatas = tokens_
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
