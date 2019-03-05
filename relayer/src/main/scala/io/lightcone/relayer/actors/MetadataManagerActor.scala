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
import io.lightcone.relayer.implicits._

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
  @inline def externalCrawlerActor = actors.get(ExternalCrawlerActor.name)

  private var tokenMetadatas = Seq.empty[TokenMetadata]
  private var tokenInfos = Seq.empty[TokenInfo]
  private var tokenTickersInUsd
    : Seq[TokenTicker] = Seq.empty[TokenTicker] // USD price
  private var marketTickers
    : Seq[MarketTicker] = Seq.empty[MarketTicker] // price represent exchange rate of market (price of market LRC-WETH is 0.01)
  private var marketMetadatas = Seq.empty[MarketMetadata]

  private var tokens = Seq.empty[Token]
  private var markets = Seq.empty[Market]

  override def initialize() = {
    val f = for {
      tokenMetadatas_ <- dbModule.tokenMetadataDal.getTokenMetadatas()
      tokenInfos_ <- dbModule.tokenInfoDal.getTokens()
      marketMetadatas_ <- dbModule.marketMetadataDal.getMarkets()
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
      tickers_ <- (externalCrawlerActor ? LoadTickers.Req())
        .mapTo[LoadTickers.Res]
    } yield {
      assert(tokenMetadatas_.nonEmpty)
      assert(tokenInfos_.nonEmpty)
      assert(marketMetadatas_ nonEmpty)
      assert(tokensUpdated nonEmpty)
      assert(tickers_.tokenTickers nonEmpty)
      assert(tickers_.marketTickers nonEmpty)
      tokenMetadatas = tokensUpdated.map(MetadataManager.normalize)
      tokenInfos = tokenInfos_
      marketMetadatas = marketMetadatas_.map(MetadataManager.normalize)
      tokenTickersInUsd = tickers_.tokenTickers
      marketTickers = tickers_.marketTickers
    }
    f onComplete {
      case Success(_) =>
        refreshTokenAndMarket()
        publish()
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
          tokens_ <- dbModule.tokenMetadataDal.getTokenMetadatas()
        } yield {
          if (result == ERR_NONE) {
            checkAndPublish(Some(tokens_), None, None)
          }
          UpdateTokenBurnRate.Res(result)
        }).sendTo(sender)
      }

    case req: InvalidateToken.Req =>
      (for {
        result <- dbModule.tokenMetadataDal
          .invalidateTokenMetadata(req.address)
        tokens_ <- dbModule.tokenMetadataDal.getTokenMetadatas()
      } yield {
        if (result == ERR_NONE) {
          checkAndPublish(Some(tokens_), None, None)
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
          checkAndPublish(None, None, Some(markets_))
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
          checkAndPublish(None, None, Some(markets_))
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
          checkAndPublish(None, None, Some(markets_))
        }
        TerminateMarket.Res(result)
      }).sendTo(sender)

    case req: TokenTickerChanged => {
      if (req.tokenTickers.nonEmpty && req.marketTickers.nonEmpty) {
        tokenTickersInUsd = req.tokenTickers
        marketTickers = req.marketTickers
        refreshTokenAndMarket()
        publish()
      }
    }
  }

  private def publish() = {
    mediator ! Publish(MetadataManagerActor.pubsubTopic, MetadataChanged())
  }

  private def checkAndPublish(
      tokenMetadatasOpt: Option[Seq[TokenMetadata]],
      tokenInfosOpt: Option[Seq[TokenInfo]],
      marketsOpt: Option[Seq[MarketMetadata]]
    ): Unit = {
    var notify = false
    tokenMetadatasOpt foreach { tokenMetadatas_ =>
      if (tokenMetadatas_ != tokenMetadatas) {
        notify = true
        tokenMetadatas = tokenMetadatas_
      }
    }

    tokenInfosOpt foreach { tokenInfos_ =>
      if (tokenInfos_ != tokenInfos) {
        notify = true
        tokenInfos = tokenInfos_
      }
    }

    marketsOpt foreach { markets_ =>
      if (markets_ != marketMetadatas) {
        notify = true
        marketMetadatas = markets_
      }
    }

    if (notify) {
      refreshTokenAndMarket()
      publish()
    }
  }

  private def syncMetadata() = {
    log.info("MetadataManagerActor run tokens and markets reload job")
    for {
      tokensMetadata_ <- dbModule.tokenMetadataDal.getTokenMetadatas()
      tokenInfos_ <- dbModule.tokenInfoDal.getTokens()
      markets_ <- dbModule.marketMetadataDal.getMarkets()
    } yield {
      checkAndPublish(Some(tokensMetadata_), Some(tokenInfos_), Some(markets_))
    }
  }

  private def refreshTokenAndMarket() = {
    if (tokenMetadatas.nonEmpty && tokenInfos.nonEmpty && tokenTickersInUsd.nonEmpty) {
      val tokenMetadataMap = tokenMetadatas.map(m => m.symbol -> m).toMap
      val tokenInfoMap = tokenInfos.map(i => i.symbol -> i).toMap
      val tokenTickerMap = tokenTickersInUsd.map(t => t.symbol -> t.price).toMap
      tokens = tokenTickersInUsd.map { t =>
        val meta = if (tokenMetadataMap.contains(t.symbol)) {
          tokenMetadataMap(t.symbol)
        } else {
          val currencyOpt = Currency.fromName(t.symbol)
          if (currencyOpt.isEmpty)
            throw ErrorException(
              ErrorCode.ERR_INTERNAL_UNKNOWN,
              s"not found Currency from symbol: ${t.symbol}"
            )
          TokenMetadata(
            symbol = t.symbol,
            address = currencyOpt.get.getAddress()
          )
        }
        val info = tokenInfoMap.getOrElse(t.symbol, TokenInfo(t.symbol))
        Token(
          Some(meta),
          Some(info),
          tokenTickerMap.getOrElse(t.symbol, 0.0)
        )
      }
    }
    if (marketMetadatas.nonEmpty && marketTickers.nonEmpty) {
      val marketTickerMap = marketTickers
        .map(m => s"${m.baseTokenSymbol}-${m.quoteTokenSymbol}" -> m)
        .toMap
      markets = marketMetadatas.map { m =>
        Market(
          Some(m),
          marketTickerMap.get(s"${m.baseTokenSymbol}-${m.quoteTokenSymbol}")
        )
      }
    }
  }
}
