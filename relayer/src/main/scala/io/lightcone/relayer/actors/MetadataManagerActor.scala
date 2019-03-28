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
import io.lightcone.ethereum.TxStatus.TX_STATUS_SUCCESS
import io.lightcone.ethereum.event._
import io.lightcone.persistence._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util._

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
    with BlockingReceive
    with ActorLogging {

  import ErrorCode._

  val selfConfig = config.getConfig(MetadataManagerActor.name)
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-dalay-in-seconds")

  val mediator = DistributedPubSub(context.system).mediator
  @inline def ethereumQueryActor = actors.get(EthereumQueryActor.name)

  private var currencies = config
    .getStringList("external_crawler.currencies")
    .asScala
    .map(_ -> 0.0)
    .toMap + ("USD" -> 1.0)

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
      blocking {
        log.debug(
          s"--MetadataManagerActor-- receive TokenBurnRateChangedEvent $req "
        )
        if (req.header.nonEmpty && req.getHeader.txStatus == TX_STATUS_SUCCESS) {
          for {
            tokens <- getTokenMetadatas()
          } yield {
            processTokenMetaChange(tokens)
          }
        } else {
          Future.unit
        }
      }

    case req: InvalidateToken.Req =>
      blocking {
        log.debug(s"--MetadataManagerActor-- receive InvalidateToken $req ")
        (for {
          result <- dbModule.tokenMetadataDal
            .invalidateTokenMetadata(req.address)
          metas <- getTokenMetadatas()
        } yield {
          if (result == ERR_NONE) {
            processTokenMetaChange(metas)
          }
          InvalidateToken.Res(result)
        }).sendTo(sender)
      }

    case req: SaveMarketMetadatas.Req =>
      blocking {
        log.debug(s"--MetadataManagerActor-- receive SaveMarketMetadatas $req ")
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
      }

    case req: UpdateMarketMetadata.Req =>
      blocking {
        log.debug(
          s"--MetadataManagerActor-- receive UpdateMarketMetadata $req "
        )
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
      }

    case req: TerminateMarket.Req =>
      blocking {
        log.debug(s"--MetadataManagerActor-- receive TerminateMarket $req ")
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
      }

    case changed: MetadataChanged => // subscribe message from ExternalCrawlerActor
      blocking {
        log.debug(
          s"--MetadataManagerActor-- receive MetadataChanged, $changed "
        )
        (for {
          _ <- if (changed.tokenMetadataChanged) {
            for {
              tokenMetas <- getTokenMetadatas()
            } yield {
              if (tokenMetas.nonEmpty) {
                processTokenMetaChange(tokenMetas)
              }
            }
          } else Future.unit
          _ <- if (changed.tokenInfoChanged) {
            for {
              tokenInfos <- getTokenInfos()
            } yield {
              if (tokenInfos.nonEmpty) {
                processTokenInfoChange(tokenInfos)
              }
            }
          } else Future.unit
          _ <- if (changed.marketMetadataChanged) {
            for {
              marketMetas <- getMarketMetas()
            } yield {
              if (marketMetas.nonEmpty) {
                processMarketMetaChange(marketMetas)
              }
            }
          } else Future.unit
          _ <- if (changed.tickerChanged) {
            for {
              tokenTickers <- getLatestTokenTickers()
            } yield {
              if (tokenTickers.nonEmpty) {
                processTokenTickerChange(tokenTickers)
              }
            }
          } else Future.unit
        } yield Unit) sendTo (sender)
      }

    case _: GetTokens.Req => //support for MetadataRefresher to synchronize tokens
      log.debug(
        s"MetadataMangerActor -- GetTokens.Req -- ${tokens.values.toSeq.mkString}"
      )
      //TODO:将currenies作为特殊token返回
      val currencyTokens = currencies.map {
        case (currency, price) =>
          Token(
            Some(TokenMetadata(symbol = currency, name = currency)),
            None,
            Some(TokenTicker(price = price))
          )
      }
      sender ! GetTokens.Res(tokens.values.toSeq ++ currencyTokens)

    case _: GetMarkets.Req =>
      sender ! GetMarkets.Res(markets.values.toSeq)
  }

  private def processTokenMetaChange(metadatas: Map[String, TokenMetadata]) = {
    val preTokens = tokens
    for {
      currentTokens <- Future.sequence(metadatas.map {
        case (symbol, meta) =>
          if (tokens.contains(symbol)) {
            Future.successful(
              symbol -> tokens(symbol).copy(metadata = Some(meta))
            )
          } else {
            for {
              infos <- dbModule.tokenInfoDal.getTokenInfos(Seq(symbol))
              tickers <- getLatestTokenTickers()
            } yield {
              symbol -> Token(
                Some(meta),
                Some(infos.headOption.getOrElse(TokenInfo(symbol = symbol))),
                Some(
                  tickers.getOrElse(symbol, TokenTicker(token = meta.address))
                )
              )
            }
          }
      })
    } yield {
      tokens = currentTokens.toMap
    }
    decideChangedEventAndPublish(preTokens, markets)
  }

  private def processTokenInfoChange(infos: Map[String, TokenInfo]) = {
    val preTokenMap = tokens
    val currentTokenMap = tokens.map {
      case (symbol, meta) =>
        symbol -> tokens(symbol).copy(info = infos.get(symbol))
    }
    tokens = currentTokenMap
    decideChangedEventAndPublish(preTokenMap, markets)
  }

  private def processTokenTickerChange(tickers: Map[String, TokenTicker]) = {
    val (preTokens, preMarkets) = (tokens, markets)
    val currentTokenMap = tokens.map {
      case (symbol, meta) =>
        symbol -> tokens(symbol).copy(ticker = tickers.get(symbol))
    }
    tokens = currentTokenMap
    val marketTickers =
      getMarketTickers(markets.map(m => m._1 -> m._2.getMetadata), tickers)
    val currentMarkets = markets.map {
      case (marketHash, market) =>
        marketHash -> market.copy(ticker = marketTickers.get(marketHash))
    }
    currencies = getCurrencies(tickers)
    markets = currentMarkets
    decideChangedEventAndPublish(preTokens, preMarkets)
  }

  private def processMarketMetaChange(metas: Map[String, MarketMetadata]) = {
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

  private def getCurrencies(tokenTickers: Map[String, TokenTicker]) = {
    currencies.map {
      case (c, _) => c -> tokenTickers.getOrElse(c, TokenTicker()).price
    } +
      ("USD" -> 1.0) +
      ("ETH" -> tokenTickers.getOrElse("ETH", TokenTicker()).price)
  }

  private def decideChangedEventAndPublish(
      preTokenMap: Map[String, Token],
      preMarketMap: Map[String, Market]
    ) = {

    log.debug(
      s"MetadataMangerActor -- decideChangedEventAndPublish -- preTokens: ${preTokenMap.mkString}, tokens: ${tokens.mkString}"
    )
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

    val changed = MetadataChanged(
      tokenMetadataChanged = preMetas != currentMetas,
      tokenInfoChanged = preInfos != currentInfos,
      tickerChanged = preTickers != currentTickers,
      marketMetadataChanged = preMarkets != currentMarkets
    )

    log.debug(
      s"MetadataMangerActor -- decideChangedEventAndPublish -- changed ${changed}"
    )
    if (changed.marketMetadataChanged || changed.tokenMetadataChanged || changed.tokenInfoChanged || changed.tickerChanged) {
      mediator ! Publish(
        MetadataManagerActor.pubsubTopic,
        changed
      )
    }
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

  def syncAndPublish() = {
    val (preTokens, preMarkets) = (tokens, markets)
    for {
      metas <- getTokenMetadatas()
      _ = log.debug(
        s"MetadataManagerAcgor -- getLatestTokens -- meta: ${metas}"
      )
      infos <- getTokenInfos()
      _ = log.debug(
        s"MetadataManagerAcgor -- getLatestTokens -- infos ${infos}"
      )
      tickers <- getLatestTokenTickers()
      _ = log.debug(
        s"MetadataManagerAcgor -- getLatestTokens -- tickers ${tickers}"
      )
      tokens_ = metas.map {
        case (symbol, metadata) =>
          symbol -> Token(
            Some(metadata),
            infos.get(symbol),
            tickers.get(symbol)
          )
      }
      currencies_ = getCurrencies(tickers)
      marketMetas <- getMarketMetas()
      marketTickers = getMarketTickers(marketMetas, tickers)
      markets_ = marketMetas.map {
        case (marketHash, meta) =>
          marketHash -> Market(
            Some(meta),
            marketTickers.get(meta.marketHash)
          )
      }
    } yield {
      tokens = tokens_
      markets = markets_
      currencies = currencies_
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

  def getMarketTickers(
      metadatas: Map[String, MarketMetadata],
      tokenTickers: Map[String, TokenTicker]
    ) = {
    metadatas.map {
      case (marketHash, meta) =>
        marketHash -> calculateMarketTicker(meta, tokenTickers)
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
      _ = log.info(
        s"### getLatestTokenTickers ${latestTime}, tickersInDb: ${tickersInDb}, tickers: ${tickers}"
      )
    } yield {
      assert(tickers.size == tickersInDb.size)
      tickers
    }

  private def calculateMarketTicker(
      market: MarketMetadata,
      tokenTickers: Map[String, TokenTicker]
    ): MarketTicker = {
    if (tokenTickers.contains(market.baseTokenSymbol) &&
        tokenTickers.contains(market.quoteTokenSymbol)) {
      val baseTicker = tokenTickers(market.baseTokenSymbol)
      val quoteTicker = tokenTickers(market.quoteTokenSymbol)
      val rate = BigDecimal(baseTicker.price / quoteTicker.price)
      val volume24H = BigDecimal(baseTicker.volume24H / baseTicker.price) * rate

      val percentChange1H =
        calcPercentChange(
          baseTicker.percentChange1H,
          quoteTicker.percentChange1H
        )
      val percentChange24H =
        calcPercentChange(
          baseTicker.percentChange24H,
          quoteTicker.percentChange24H
        )
      val percentChange7D =
        calcPercentChange(
          baseTicker.percentChange7D,
          quoteTicker.percentChange7D
        )

      MarketTicker(
        market.getMarketPair.baseToken,
        market.getMarketPair.quoteToken,
        rate.scaleDoubleValue(market.priceDecimals),
        baseTicker.price,
        volume24H.scaleDoubleValue(market.priceDecimals),
        percentChange1H,
        percentChange24H,
        percentChange7D
      )
    } else {
      //TODO:如果tokenTicker不存在时，marketTicker如何处理
      MarketTicker(
        market.getMarketPair.baseToken,
        market.getMarketPair.quoteToken
      )
    }
  }

  private def calcPercentChange(
      v1: Double,
      v2: Double
    ) = BigDecimal((1 + v1) / (1 + v2) - 1).scaleDoubleValue()

  implicit class RichBigDecimal(v: BigDecimal) {

    def scaleDoubleValue(scale: Int = 2): Double = {
      scala.util
        .Try(v.setScale(scale, BigDecimal.RoundingMode.HALF_UP).toDouble)
        .toOption
        .getOrElse(0)
    }
  }
}
