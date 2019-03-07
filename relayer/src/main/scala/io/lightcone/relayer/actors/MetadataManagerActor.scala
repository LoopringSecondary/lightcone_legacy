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
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
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
  private var tokenTickers: Seq[TokenTickerRecord] =
    Seq.empty[TokenTickerRecord]
  private var marketTickers: Seq[MarketTicker] = Seq.empty[MarketTicker]
  private var marketMetadatas = Seq.empty[MarketMetadata]

  private var tokens = Seq.empty[Token]
  private var markets = Seq.empty[Market]

  override def initialize() = {
    val f = for {
      _ <- mediator ? Subscribe(ExternalCrawlerActor.pubsubTopic, self)
      tokenMetadatas_ <- dbModule.tokenMetadataDal.getTokenMetadatas()
      tokenInfos_ <- dbModule.tokenInfoDal.getTokenInfos()
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
      tickers_ <- getLastTickers()
    } yield {
      assert(tokenMetadatas_.nonEmpty)
      assert(tokenInfos_.nonEmpty)
      assert(marketMetadatas_ nonEmpty)
      assert(tokensUpdated nonEmpty)
      assert(tickers_ nonEmpty)
      tokenMetadatas = tokensUpdated.map(MetadataManager.normalize)
      tokenInfos = tokenInfos_
      marketMetadatas = marketMetadatas_.map(MetadataManager.normalize)
      tokenTickers = tickers_
      marketTickers = fillSupportMarketTickers(tokenTickers)
      refreshTokenAndMarket()
    }
    f onComplete {
      case Success(_) =>
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

    case _: TokenTickerChanged => { // subscribe message from ExternalCrawlerActor
      for {
        tickers_ <- getLastTickers()
      } yield {
        if (tickers_.nonEmpty) {
          tokenTickers = tickers_
          marketTickers = fillSupportMarketTickers(tickers_)
          refreshTokenAndMarket()
          publish()
        }
      }
    }

    case _: GetTokens.Req =>
      sender ! GetTokens
        .Res(tokens) // support for MetadataRefresher to synchronize tokens

    case _: GetMarkets.Req => sender ! GetMarkets.Res(markets)
  }

  private def getLastTickers(): Future[Seq[TokenTickerRecord]] =
    for {
      latestEffectiveTime <- dbModule.tokenTickerRecordDal
        .getLastTicker()
      tickers_ <- if (latestEffectiveTime.nonEmpty) {
        dbModule.tokenTickerRecordDal.getTickers(
          latestEffectiveTime.get
        )
      } else {
        Future.successful(Seq.empty)
      }
    } yield tickers_

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
      tokenMetadatas_ <- dbModule.tokenMetadataDal.getTokenMetadatas()
      tokenInfos_ <- dbModule.tokenInfoDal.getTokenInfos()
      marketMetadatas_ <- dbModule.marketMetadataDal.getMarkets()
    } yield {
      checkAndPublish(
        Some(tokenMetadatas_),
        Some(tokenInfos_),
        Some(marketMetadatas_)
      )
    }
  }

  private def refreshTokenAndMarket(): Unit = this.synchronized {
    if (tokenMetadatas.nonEmpty && tokenInfos.nonEmpty && tokenTickers.nonEmpty) {
      val tokenMetadataMap = tokenMetadatas.map(m => m.symbol -> m).toMap
      val tokenInfoMap = tokenInfos.map(i => i.symbol -> i).toMap
      tokens = tokenTickers.map { ticker =>
        val symbol = ticker.symbol
        val meta =
          tokenMetadataMap.getOrElse(symbol, TokenMetadata(symbol = symbol))
        val info = tokenInfoMap.getOrElse(symbol, TokenInfo(symbol = symbol))
        Token(
          Some(meta),
          Some(info),
          ticker.price
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

  private def fillSupportMarketTickers(
      usdTickers: Seq[TokenTickerRecord]
    ): Seq[MarketTicker] = {
    val effectiveMarket = metadataManager
      .getMarkets()
      .filter(_.metadata.get.status != MarketMetadata.Status.TERMINATED)
      .map(_.metadata.get)
    effectiveMarket.map(m => calculateMarketQuote(m, usdTickers))
  }

  private def calculateMarketQuote(
      market: MarketMetadata,
      usdTickers: Seq[TokenTickerRecord]
    ): MarketTicker = {
    val baseTicker = getTickerBySymbol(market.baseTokenSymbol, usdTickers)
    val quoteTicker = getTickerBySymbol(market.quoteTokenSymbol, usdTickers)
    val rate = toDouble(BigDecimal(baseTicker.price / quoteTicker.price))
    val volume24H = toDouble(
      BigDecimal(baseTicker.volume24H / baseTicker.price) * rate
    )
//    val market_cap = toDouble(
//      BigDecimal(baseTicker.marketCap / baseTicker.price) * rate
//    )
    val percentChange1H =
      calc(baseTicker.percentChange1H, quoteTicker.percentChange1H)
    val percentChange24H =
      calc(baseTicker.percentChange24H, quoteTicker.percentChange24H)
    val percentChange7D =
      calc(baseTicker.percentChange7D, quoteTicker.percentChange7D)
    MarketTicker(
      market.baseTokenSymbol,
      market.quoteTokenSymbol,
      rate,
      baseTicker.price,
      volume24H,
      toDouble(percentChange1H),
      toDouble(percentChange24H),
      toDouble(percentChange7D)
    )
  }

  private def getTickerBySymbol(
      symbol: String,
      usdTickers: Seq[TokenTickerRecord]
    ) = {
    usdTickers
      .find(t => t.symbol == symbol)
      .getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found ticker of symbol: $symbol"
        )
      )
  }

  private def calc(
      v1: Double,
      v2: Double
    ) =
    BigDecimal((1 + v1) / (1 + v2) - 1)
      .setScale(2, BigDecimal.RoundingMode.HALF_UP)
      .toDouble

  private def toDouble(bigDecimal: BigDecimal): Double =
    scala.util
      .Try(bigDecimal.setScale(8, BigDecimal.RoundingMode.HALF_UP).toDouble)
      .toOption
      .getOrElse(0)
}
