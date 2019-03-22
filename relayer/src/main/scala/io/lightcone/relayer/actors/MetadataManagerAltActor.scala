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

  private var tokenMap = Map.empty[String, Token]
  private var marketMap = Map.empty[String, Market]

  private def assembleTokens(metadatas:Map[String, TokenMetadata], infos:Map[String,TokenInfo], tickers:Map[String, TokenTicker]) = {
    val tokenMapTmp = metadatas.map {
      case (symbol, metadata) =>
        symbol -> Token(
          Some(metadata),
          infos.get(symbol),
          tickers.get(symbol)
        )
    }
    val preTokenMap = tokenMap
    tokenMap = tokenMapTmp
    decideChangedEventAndPublish(preTokenMap, marketMap)
  }

  private def processTokenMetaChange(metadatas: Map[String, TokenMetadata]) = {
    val preTokenMap = tokenMap
    val currentTokenMap = metadatas.map {
      case (symbol,meta) =>
        if (tokenMap.contains(symbol)) {
        symbol -> tokenMap(symbol)
      } else {
          symbol -> Token(
            Some(meta),
            Some(TokenInfo(symbol = symbol)),
            Some(TokenTicker(token = meta.address))
          )
        }
    }
    tokenMap = currentTokenMap
    decideChangedEventAndPublish(preTokenMap, marketMap)
  }

  private def processTokenInfoChange(infos: Map[String,TokenInfo]) = {
    val preTokenMap = tokenMap
    val currentTokenMap = tokenMap.map {
      case (symbol,meta) =>
          symbol -> tokenMap(symbol).copy(info = infos.get(symbol))
    }
    tokenMap = currentTokenMap
    decideChangedEventAndPublish(preTokenMap, marketMap)
  }

  private def processTokenTickerChange(tickers: Map[String, TokenTicker]) = {
    val preTokenMap = tokenMap
    val currentTokenMap = tokenMap.map {
      case (symbol,meta) =>
        symbol -> tokenMap(symbol).copy(ticker = tickers.get(symbol))
    }
    tokenMap = currentTokenMap
    decideChangedEventAndPublish(preTokenMap, marketMap)
  }

  private def decideChangedEventAndPublish(preTokenMap: Map[String, Token], preMarketMap: Map[String, Market]) = {
    val (preMetas, preInfos, preTickers) = preTokenMap.values
      .toSeq
      .sortBy(_.getMetadata.symbol)
      .unzip3(t => (t.getMetadata, t.getInfo, t.getTicker))

    val (currentMetas, currentInfos, currentTickers) = tokenMap.values
      .toSeq.
      sortBy(_.getMetadata.symbol)
      .unzip3(t => (t.getMetadata, t.getInfo, t.getTicker))

    mediator ! MetadataChanged(
      tokenMetadataChanged = preMetas == currentMetas,
      tokenInfoChanged = preInfos == currentInfos,
      tickerChanged = preTickers == currentTickers
    )
  }

  private def getTokenMetadatas() = for {
    tokenMetadatasInDb <- dbModule.tokenMetadataDal.getTokenMetadatas()
    batchBurnRateReq = BatchGetBurnRate.Req(
      reqs = tokenMetadatasInDb.map(
        meta => GetBurnRate.Req(meta.address)
      )
    )
    burnRates <- (ethereumQueryActor ? batchBurnRateReq).mapTo[BatchGetBurnRate.Res].map(_.resps)
    tokenMetadatas <- Future.sequence(tokenMetadatasInDb.zipWithIndex.map {
      case (meta,idx) =>
        val currentBurnRateOpt = burnRates(idx).burnRate
        for {
        _ <- if (currentBurnRateOpt.nonEmpty && currentBurnRateOpt.get != meta.burnRate.get) {
          dbModule.tokenMetadataDal
            .updateBurnRate(
              meta.address,
              currentBurnRateOpt.get.forMarket,
              currentBurnRateOpt.get.forP2P
            )
        }
        else Future.unit
        newMeta = MetadataManager.normalize(meta.copy(burnRate = currentBurnRateOpt))
        } yield meta.symbol -> newMeta
    })
  } yield tokenMetadatas.toMap

  private def getTokenInfos() = for {
    tokenInfos_ <- dbModule.tokenInfoDal.getTokenInfos()
    infos = tokenInfos_.map(info => info.symbol -> info).toMap
  } yield infos

  override def initialize() = {
    val f = for {
      tokenMetadatas <- getTokenMetadatas()
      infos <- getTokenInfos()
      tokenTickers <- getLastTickers()

      marketMetadatas_ <- dbModule.marketMetadataDal.getMarkets()
      marketMetadatas = marketMetadatas_.map(MetadataManager.normalize)
      marketTickers = fillSupportMarketTickers(tokenTickers)
    } yield {
      assert(tokenMetadatas.nonEmpty)
      assert(infos.nonEmpty)
      assert(marketMetadatas_ nonEmpty)
      assert(tokenTickers nonEmpty)

      assembleTokens(tokenMetadatas, infos, tokenTickers)

      setTokenAndMarket()
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
            processTokenMetaChange(tokens_.map(t => t.symbol -> t).toMap)
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

    case MetadataChanged(false, false, false, true) => { // subscribe message from ExternalCrawlerActor
      for {
        tickers_ <- getLastTickers()
      } yield {
        if (tickers_.nonEmpty) {
          tokenTickers = tickers_
          marketTickers = fillSupportMarketTickers(tickers_)
          setTokenAndMarket()
          publish(false, false, false, true)
        }
      }
    }

    case _: GetTokens.Req =>
      sender ! GetTokens
        .Res(tokens) // support for MetadataRefresher to synchronize tokens

    case _: GetMarkets.Req => sender ! GetMarkets.Res(markets)
  }

  private def getLastTickers(): Future[Map[String, TokenTicker]] =
    for {
      latestEffectiveTime <- dbModule.tokenTickerRecordDal
        .getLastTickerTime()
      tickers_ <- if (latestEffectiveTime.nonEmpty) {
        dbModule.tokenTickerRecordDal.getTickers(
          latestEffectiveTime.get
        )
      } else {
        Future.successful(Seq.empty)
      }
    tickers = tickers_.map{
      tickerRecord =>
      val ticker:TokenTicker = tickerRecord
        tickerRecord.symbol -> ticker
    }.toMap
    assert(tickers.size == tickers_.size)
    } yield tickers

  private def publish(
      tokenMetadataChanged: Boolean,
      tokenInfoChanged: Boolean,
      marketMetadataChanged: Boolean,
      tickerChanged: Boolean
    ) = {
    mediator ! Publish(
      MetadataManagerActor.pubsubTopic,
      MetadataChanged(
        tokenMetadataChanged,
        tokenInfoChanged,
        marketMetadataChanged,
        tickerChanged
      )
    )
  }

  private def checkAndPublish(
      tokenMetadatasOpt: Option[Seq[TokenMetadata]],
      tokenInfosOpt: Option[Seq[TokenInfo]],
      marketsOpt: Option[Seq[MarketMetadata]]
    ): Unit = {
    var tokenMetadataChanged = false
    var tokenInfoChanged = false
    var marketMetadataChanged = false
    tokenMetadatasOpt foreach { tokenMetadatas_ =>
      if (tokenMetadatas_ != tokenMetadatas) {
        tokenMetadataChanged = true
        tokenMetadatas = tokenMetadatas_
      }
    }

    tokenInfosOpt foreach { tokenInfos_ =>
      if (tokenInfos_ != tokenInfos) {
        tokenInfoChanged = true
        tokenInfos = tokenInfos_
      }
    }

    marketsOpt foreach { markets_ =>
      if (markets_ != marketMetadatas) {
        marketMetadataChanged = true
        marketMetadatas = markets_
      }
    }

    if (tokenMetadataChanged || tokenInfoChanged || marketMetadataChanged) {
      setTokenAndMarket()
      publish(
        tokenMetadataChanged,
        tokenInfoChanged,
        marketMetadataChanged,
        false
      )
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

  private def setTokenAndMarket(): Unit = this.synchronized {
    if (tokenMetadatas.nonEmpty && tokenInfos.nonEmpty && tokenTickers.nonEmpty) {
      val tokenInfoMap = tokenInfos.map(i => i.symbol -> i).toMap
      val tickerMap = tokenTickers.map(t => t.symbol -> t).toMap
      val metadataTokens = tokenMetadatas.map { m =>
        val symbol = m.symbol
        val info = tokenInfoMap.getOrElse(symbol, TokenInfo(symbol = symbol))
        val tickerRecord =
          tickerMap.getOrElse(symbol, TokenTickerRecord(symbol = symbol))
        val tokenTicker: TokenTicker = tickerRecord
        Token(
          Some(m),
          Some(info),
          Some(tokenTicker)
        )
      }
      val extraInTickers =
        tokenTickers.map(_.symbol).diff(tokenMetadatas.map(_.symbol)).map {
          symbol =>
            val tokenTicker: TokenTicker = tickerMap(symbol)
            Token(
              Some(
                TokenMetadata(
                  `type` = TokenMetadata.Type.TOKEN_TYPE_ETH,
                  symbol = symbol
                )
              ),
              Some(TokenInfo(symbol = symbol)),
              Some(tokenTicker)
            )
        }
      tokens = metadataTokens ++ extraInTickers
    }
    if (marketMetadatas.nonEmpty && marketTickers.nonEmpty) {
      val marketTickerMap = marketTickers.map { m =>
        MarketHash(MarketPair(m.baseToken, m.quoteToken)).hashString() -> m
      }.toMap
      markets = marketMetadatas.map { m =>
        Market(
          Some(m),
          marketTickerMap.get(MarketHash(m.marketPair.get).hashString())
        )
      }
    }
  }

  //TODO：为什么只取可用的market的ticker？
//  private def fillSupportMarketTickers(
//      tokenTickers: Map[String,TokenTicker]
//    ): Map[String, Option[MarketTicker]] = {
//    val effectiveMarket = metadataManager
//      .getMarkets()
//      .filter(_.metadata.get.status != MarketMetadata.Status.TERMINATED)
//      .map(_.metadata.get)
//    effectiveMarket.map(m => m.marketHash -> calculateMarketQuote(m, tokenTickers)).toMap
//  }

  private def calculateMarketTicker(
      market: MarketMetadata,
      tokenTickers: Map[String, TokenTicker]
    ): Option[MarketTicker] = {
    if (tokenTickers.contains(market.baseTokenSymbol) && tokenTickers.contains(market.quoteTokenSymbol)) {
      val baseTicker = tokenTickers(market.baseTokenSymbol)
      val quoteTicker = tokenTickers(market.quoteTokenSymbol)
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
      Some(MarketTicker(
        market.marketPair.get.baseToken,
        market.marketPair.get.quoteToken,
        rate,
        baseTicker.price,
        volume24H,
        toDouble(percentChange1H),
        toDouble(percentChange24H),
        toDouble(percentChange7D)
      ))
    } else {
      //TODO:如果tokenTicker不存在时，marketTicker如何处理
      None
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
