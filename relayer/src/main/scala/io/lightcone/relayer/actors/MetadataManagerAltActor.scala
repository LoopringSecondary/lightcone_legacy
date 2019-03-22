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
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.core._
import io.lightcone.ethereum.event._
import io.lightcone.persistence._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.implicits._

import scala.concurrent.{ExecutionContext, Future}
import scala.util._

object MetadataManagerAltActor extends DeployedAsSingleton {
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

class MetadataManagerAltActor(
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

  private var tokens = Map.empty[String, Token]
  private var markets = Map.empty[String, Market]

  override def initialize() = {
    val (preTokens, preMarkets) = (tokens, markets)
    val f = syncAndPublish
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
      run = () => syncAndPublish()
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
            processTokenMetaChange(tokens_.map(t => t.symbol -> t).toMap)
          }
          UpdateTokenBurnRate.Res(result)
        }).sendTo(sender)
      }

    case req: InvalidateToken.Req =>
      (for {
        result <- dbModule.tokenMetadataDal
          .invalidateTokenMetadata(req.address)
        metasInDb <- dbModule.tokenMetadataDal.getTokenMetadatas()
        metas = metasInDb.map(m => m.symbol -> m).toMap
      } yield {
        if (result == ERR_NONE) {
          processTokenMetaChange(metas)
        }
        InvalidateToken.Res(result)
      }).sendTo(sender)

    case req: SaveMarketMetadatas.Req =>
      (for {
        saved <- dbModule.marketMetadataDal
          .saveMarkets(req.markets)
        metas <- getMarketMetas()
      } yield {
        if (saved.nonEmpty) {
          processMarketMetaChange(metas)
        }
        SaveMarketMetadatas.Res(saved)
      }).sendTo(sender)

    case req: UpdateMarketMetadata.Req =>
      (for {
        result <- dbModule.marketMetadataDal
          .updateMarket(req.market.get)
        metas <- getMarketMetas()
      } yield {
        if (result == ERR_NONE) {
          processMarketMetaChange(metas)
        }
        UpdateMarketMetadata.Res(result)
      }).sendTo(sender)

    case req: TerminateMarket.Req =>
      (for {
        result <- dbModule.marketMetadataDal
          .terminateMarketByKey(req.marketHash)
        metas <- getMarketMetas()
      } yield {
        if (result == ERR_NONE) {
          processMarketMetaChange(metas)
        }
        TerminateMarket.Res(result)
      }).sendTo(sender)

    case MetadataChanged(false, false, false, true) => // subscribe message from ExternalCrawlerActor
      for {
        tokenTickers <- getLatestTokenTickers()
      } yield {
        if (tokenTickers.nonEmpty) {
          processTokenTickerChange(tokenTickers)
        }
      }

    case _: GetTokens.Req => // support for MetadataRefresher to synchronize tokens
      sender ! GetTokens.Res(tokens.values.toSeq)

    case _: GetMarkets.Req =>
      sender ! GetMarkets.Res(markets.values.toSeq)
  }

  private def processTokenMetaChange(metadatas: Map[String, TokenMetadata]) =
    tokens.synchronized {
      val preTokenMap = tokens
      val currentTokenMap = metadatas.map {
        case (symbol, meta) =>
          if (tokens.contains(symbol)) {
            symbol -> tokens(symbol)
          } else {
            symbol -> Token(
              Some(meta),
              Some(TokenInfo(symbol = symbol)),
              Some(TokenTicker(token = meta.address))
            )
          }
      }
      tokens = currentTokenMap
      decideChangedEventAndPublish(preTokenMap, markets)
    }

  private def processTokenInfoChange(infos: Map[String, TokenInfo]) =
    tokens.synchronized {
      val preTokenMap = tokens
      val currentTokenMap = tokens.map {
        case (symbol, meta) =>
          symbol -> tokens(symbol).copy(info = infos.get(symbol))
      }
      tokens = currentTokenMap
      decideChangedEventAndPublish(preTokenMap, markets)
    }

  private def processTokenTickerChange(tickers: Map[String, TokenTicker]) =
    tokens.synchronized {
      val preTokenMap = tokens
      val currentTokenMap = tokens.map {
        case (symbol, meta) =>
          symbol -> tokens(symbol).copy(ticker = tickers.get(symbol))
      }
      tokens = currentTokenMap
      decideChangedEventAndPublish(preTokenMap, markets)
    }

  private def processMarketMetaChange(metas: Map[String, MarketMetadata]) =
    markets.synchronized {
      val preMarkets = markets
      val currentMarkets = metas.map {
        case (symbol, meta) =>
          if (markets.contains(symbol)) {
            symbol -> markets(symbol).copy(metadata = metas.get(symbol))
          } else {
            symbol -> Market(
              Some(meta),
              Some(
                MarketTicker(
                  baseToken = meta.getMarketPair.baseToken,
                  quoteToken = meta.getMarketPair.quoteToken
                )
              )
            )
          }

      }
      markets = currentMarkets
      decideChangedEventAndPublish(tokens, preMarkets)
    }

  private def decideChangedEventAndPublish(
      preTokenMap: Map[String, Token],
      preMarketMap: Map[String, Market]
    ) = {
    val (preMetas, preInfos, preTickers) = preTokenMap.values.toSeq
      .sortBy(_.getMetadata.symbol)
      .unzip3(t => (t.getMetadata, t.getInfo, t.getTicker))

    val (currentMetas, currentInfos, currentTickers) = tokens.values.toSeq
      .sortBy(_.getMetadata.symbol)
      .unzip3(t => (t.getMetadata, t.getInfo, t.getTicker))

    val preMarkets = preMarketMap
      .map(_._2.getMetadata)
      .toSeq
      .sortBy(_.marketHash)
    val currentMarkets = markets
      .map(_._2.getMetadata)
      .toSeq
      .sortBy(_.marketHash)
    mediator ! Publish(
      MetadataManagerActor.pubsubTopic,
      MetadataChanged(
        tokenMetadataChanged = preMetas == currentMetas,
        tokenInfoChanged = preInfos == currentInfos,
        tickerChanged = preTickers == currentTickers,
        marketMetadataChanged = preMarkets == currentMarkets
      )
    )
  }

  private def getTokenMetadatas() =
    for {
      tokenMetadatasInDb <- dbModule.tokenMetadataDal.getTokenMetadatas()
      batchBurnRateReq = BatchGetBurnRate.Req(
        reqs = tokenMetadatasInDb.map(
          meta => GetBurnRate.Req(meta.address)
        )
      )
      burnRates <- (ethereumQueryActor ? batchBurnRateReq)
        .mapTo[BatchGetBurnRate.Res]
        .map(_.resps)
      tokenMetadatas <- Future.sequence(tokenMetadatasInDb.zipWithIndex.map {
        case (meta, idx) =>
          val currentBurnRateOpt = burnRates(idx).burnRate
          for {
            _ <- if (currentBurnRateOpt.nonEmpty && currentBurnRateOpt.get != meta.burnRate.get) {
              dbModule.tokenMetadataDal
                .updateBurnRate(
                  meta.address,
                  currentBurnRateOpt.get.forMarket,
                  currentBurnRateOpt.get.forP2P
                )
            } else Future.unit
            newMeta = MetadataManager.normalize(
              meta.copy(burnRate = currentBurnRateOpt)
            )
          } yield meta.symbol -> newMeta
      })
    } yield tokenMetadatas.toMap

  private def getTokenInfos() =
    for {
      tokenInfos_ <- dbModule.tokenInfoDal.getTokenInfos()
      infos = tokenInfos_.map(info => info.symbol -> info).toMap
    } yield infos

  def getLatestTokens() =
    for {
      metas <- getTokenMetadatas()
      infos <- getTokenInfos()
      tickers <- getLatestTokenTickers()
      tokens_ = metas.map {
        case (symbol, metadata) =>
          symbol -> Token(
            Some(metadata),
            infos.get(symbol),
            tickers.get(symbol)
          )
      }
    } yield {
      assert(metas.nonEmpty)
      assert(infos.nonEmpty)
      assert(tickers nonEmpty)
      tokens_
    }

  def getLatestMarkets() =
    for {
      metas <- getMarketMetas()
      tickers = getMarketTickers(metas)
      markets_ = metas.map {
        case (marketHash, meta) =>
          marketHash -> Market(
            Some(meta),
            tickers.get(meta.marketHash)
          )
      }
    } yield {
      assert(metas nonEmpty)
      markets_
    }

  def syncAndPublish() = {
    val (preTokens, preMarkets) = (tokens, markets)
    for {
      tokens_ <- getLatestTokens()
      markets_ <- getLatestMarkets()
    } yield {
      tokens = tokens_
      markets = markets_
      decideChangedEventAndPublish(preTokens, preMarkets)
    }
  }

  def getMarketMetas() =
    for {
      marketMetadatas_ <- dbModule.marketMetadataDal.getMarkets()
      marketMetadatas = marketMetadatas_.map { meta =>
        meta.marketHash -> MetadataManager.normalize(meta)
      }
    } yield marketMetadatas.toMap

  def getMarketTickers(metadatas: Map[String, MarketMetadata]) = {
    metadatas.map {
      case (marketHash, meta) => marketHash -> calculateMarketTicker(meta)
    }
  }

  private def getLatestTokenTickers(): Future[Map[String, TokenTicker]] =
    for {
      latestTime <- dbModule.tokenTickerRecordDal
        .getLastTickerTime()
      tickersInDb <- dbModule.tokenTickerRecordDal.getTickers(latestTime.get)
      tickers = tickersInDb.map { tickerRecord =>
        val ticker: TokenTicker = tickerRecord
        tickerRecord.symbol -> ticker
      }.toMap
    } yield {
      assert(tickers.size == tickersInDb.size)
      tickers
    }

  private def calculateMarketTicker(market: MarketMetadata): MarketTicker = {
    if (tokens.contains(market.baseTokenSymbol) && tokens.contains(
          market.quoteTokenSymbol
        )) {
      val baseTicker = tokens(market.baseTokenSymbol).getTicker
      val quoteTicker = tokens(market.quoteTokenSymbol).getTicker
      val rate = toDouble(BigDecimal(baseTicker.price / quoteTicker.price))
      val volume24H = toDouble(
        BigDecimal(baseTicker.volume24H / baseTicker.price) * rate
      )

      val percentChange1H =
        calc(baseTicker.percentChange1H, quoteTicker.percentChange1H)
      val percentChange24H =
        calc(baseTicker.percentChange24H, quoteTicker.percentChange24H)
      val percentChange7D =
        calc(baseTicker.percentChange7D, quoteTicker.percentChange7D)
      MarketTicker(
        market.marketPair.get.baseToken,
        market.marketPair.get.quoteToken,
        rate,
        baseTicker.price,
        volume24H,
        toDouble(percentChange1H),
        toDouble(percentChange24H),
        toDouble(percentChange7D)
      )
    } else {
      //TODO:如果tokenTicker不存在时，marketTicker如何处理
      MarketTicker(
        market.marketPair.get.baseToken,
        market.marketPair.get.quoteToken
      )
    }
  }

  private def calc(
      v1: Double,
      v2: Double
    ) =
    BigDecimal((1 + v1) / (1 + v2) - 1)
      .setScale(2, BigDecimal.RoundingMode.HALF_UP)
      .toDouble

  private def toDouble(bigDecimal: BigDecimal): Double =
    //TODO:8是哪里来的？不是应该是market.priceDecimals???
    scala.util
      .Try(bigDecimal.setScale(8, BigDecimal.RoundingMode.HALF_UP).toDouble)
      .toOption
      .getOrElse(0)
}
