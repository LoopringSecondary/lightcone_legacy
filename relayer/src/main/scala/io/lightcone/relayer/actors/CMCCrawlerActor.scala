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

import java.text.SimpleDateFormat

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.cmc._
import io.lightcone.core.{ErrorCode, ErrorException, TokenMetadata}
import io.lightcone.lib._
import io.lightcone.persistence._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.external.TickerRequest
import scala.concurrent.{ExecutionContext, Future}
import io.lightcone.relayer.jsonrpc._
import scala.util.{Failure, Success}

// Owner: Yongfeng
object CMCCrawlerActor extends DeployedAsSingleton {
  val name = "cmc_crawler"
  val pubsubTopic = "TOKEN_TICKER_CHANGE"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      dbModule: DatabaseModule,
      actors: Lookup[ActorRef],
      materializer: ActorMaterializer,
      tickerRequest: TickerRequest,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new CMCCrawlerActor()))
  }
}

class CMCCrawlerActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val materializer: ActorMaterializer,
    val dbModule: DatabaseModule,
    val system: ActorSystem,
    val tickerRequest: TickerRequest)
    extends InitializationRetryActor
    with JsonSupport
    with RepeatedJobActor
    with ActorLogging {

  val selfConfig = config.getConfig(CMCCrawlerActor.name)
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-dalay-in-seconds")

  val mediator = DistributedPubSub(context.system).mediator

  private var tickers: Seq[CMCTickersInUsd] = Seq.empty[CMCTickersInUsd]

  val repeatedJobs = Seq(
    Job(
      name = "sync_cmc_datas",
      dalayInSeconds = refreshIntervalInSeconds,
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => syncFromCMC()
    )
  )

  override def initialize() = {
    val f = for {
      latestJob <- dbModule.CMCRequestJobDal.findLatest()
      tickers_ <- if (latestJob.nonEmpty) {
        dbModule.CMCTickersInUsdDal.getTickersByJob(latestJob.get.batchId)
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      if (tickers_ nonEmpty) {
        tickers = tickers_
        mediator ! Publish(CMCCrawlerActor.pubsubTopic, TokenTickerChanged())
      }
    }
    f onComplete {
      case Success(_) =>
        becomeReady()
      case Failure(e) =>
        throw e
    }
    f
  }

  def ready: Receive = super.receiveRepeatdJobs orElse {
    case _: GetTickers.Req =>
      sender ! GetTickers.Res(tickers)
  }

  private def syncFromCMC() = this.synchronized {
    log.info("CMCCrawlerActor run sync job")
    for {
      savedJob <- dbModule.CMCRequestJobDal.saveJob(
        CMCRequestJob(requestTime = timeProvider.getTimeSeconds())
      )
      cmcResponse <- tickerRequest.requestCMCTickers()
      updated <- if (cmcResponse.data.nonEmpty) {
        val status = cmcResponse.status.getOrElse(TickerStatus())
        for {
          _ <- dbModule.CMCRequestJobDal.updateStatusCode(
            savedJob.batchId,
            status.errorCode,
            timeProvider.getTimeSeconds()
          )
          _ <- persistTickers(savedJob.batchId, cmcResponse.data)
          _ <- dbModule.CMCRequestJobDal.updateSuccessfullyPersisted(
            savedJob.batchId
          )
          tokens <- dbModule.tokenMetadataDal.getTokens()
          _ <- updateTokenPrice(cmcResponse.data, tokens)
        } yield true
      } else {
        Future.successful(false)
      }
    } yield {
      if (updated) {
        // TODO tokenMetadata reload
      }
      publish()
    }
  }

//  {
//    marketTickers = getMarketUSDQuote(usdTickers)
//    tickersWithAllSupportMarket = fillInAllSupportMarkets(
//      usdTickers,
//      marketTickers
//    )
//    _ <- updateTickers(tickersWithAllSupportMarket)
//    tokens <- dbModule.tokenMetadataDal.getTokens()
//    _ <- updateTokenPrice(usdTickers, tokens)
//  }

  private def publish() = {
    mediator ! Publish(CMCCrawlerActor.pubsubTopic, TokenTickerChanged())
  }

  private def updateTokenPrice(
      usdTickers: Seq[CMCTickerData],
      tokens: Seq[TokenMetadata]
    ) = {
    var changedTokens = Seq.empty[TokenMetadata]
    tokens.foreach { token =>
      val symbol = if (token.symbol == "WETH") "ETH" else token.symbol
      val priceQuote =
        usdTickers.find(_.symbol == symbol).flatMap(_.quote.get("USD"))
      val usdPriceQuote = priceQuote.getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"can not found ${symbol} price in USD"
        )
      )
      if (token.usdPrice != usdPriceQuote.price) {
        changedTokens = changedTokens :+ token.copy(
          usdPrice = usdPriceQuote.price
        )
      }
    }
    Future.sequence(changedTokens.map { token =>
      dbModule.tokenMetadataDal
        .updateTokenPrice(token.address, token.usdPrice)
        .map { r =>
          if (r != ErrorCode.ERR_NONE)
            log.error(s"failed to update token price:$token")
        }
    })
  }

  private def persistTickers(
      batchId: Int,
      tickers_ : Seq[CMCTickerData]
    ) =
    for {
      _ <- Future.unit
      tickersToPersist = convertResponseToPersist(batchId, tickers_)
      _ = tickers = tickersToPersist
      fixGroup = tickersToPersist.grouped(20).toList
      _ = fixGroup.map(dbModule.CMCTickersInUsdDal.saveTickers)
    } yield Unit

  private def convertResponseToPersist(
      batchId: Int,
      tickers_ : Seq[CMCTickerData]
    ) = {
    tickers_.map { t =>
      val usdQuote = if (t.quote.get("USD").isEmpty) {
        log.error(s"CMC not return ${t.symbol} quote for USD")
        CMCTickersInUsd.Quote()
      } else {
        val q = t.quote("USD")
        CMCTickersInUsd.Quote(
          q.price,
          q.volume24H,
          q.percentChange1H,
          q.percentChange24H,
          q.percentChange7D,
          q.marketCap,
          q.lastUpdated
        )
      }
      CMCTickersInUsd(
        t.id,
        t.name,
        t.symbol,
        t.slug,
        t.circulatingSupply,
        t.totalSupply,
        t.maxSupply,
        t.dateAdded,
        t.numMarketPairs,
        t.cmcRank,
        t.lastUpdated,
        Some(usdQuote),
        batchId
      )
    }
  }

  private def fillInAllSupportMarkets(
      usdTickers: Seq[CMCTickerData],
      marketTickers: Seq[(String, Quote)]
    ) = {
    usdTickers.map { usdTicker =>
      val usdQuote = usdTicker.quote.get("USD") //先找到每个token的usd quote
      val ticker = if (usdQuote.isEmpty) {
        log.error(s"can not found ${usdTicker.symbol} quote for USD")
        usdTicker
      } else {
        val priceQuote = usdQuote.get
        val quoteMap = marketTickers.foldLeft(usdTicker.quote) {
          (map, marketQuote) =>
            //添加市场代币的Quote ("LRC", "WETH", "TUSD", "USDT")
            map + (marketQuote._1 -> convertQuote(priceQuote, marketQuote._2))
        }
        //更新token的quote属性
        usdTicker.copy(quote = quoteMap)
      }
      ticker
    }
  }

  //找到市场代币对USD的priceQuote
  private def getMarketUSDQuote(
      tickers: Seq[CMCTickerData]
    ): Seq[(String, Quote)] = {
    markets.map { s =>
      val symbol = if (s == "WETH") "ETH" else s
      val priceQuote =
        tickers.find(_.symbol == symbol).flatMap(_.quote.get("USD"))
      (
        s,
        priceQuote.getOrElse(
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"can not found ${s} price in USD"
          )
        )
      )
    }
  }

  //锚定市场币的priceQuote换算
  private def convertQuote(
      tokenQuote: Quote,
      marketQuote: Quote
    ): Quote = {
    val price = toDouble(BigDecimal(tokenQuote.price / marketQuote.price))
    val volume_24h = toDouble(
      BigDecimal(tokenQuote.volume24H / tokenQuote.price) * price
    )
    val market_cap = toDouble(
      BigDecimal(tokenQuote.marketCap / tokenQuote.price) * price
    )
    val percent_change_1h = BigDecimal(1 + tokenQuote.percentChange1H) / BigDecimal(
      1 + marketQuote.percentChange1H
    ) - 1
    val percent_change_24h = BigDecimal(1 + tokenQuote.percentChange24H) / BigDecimal(
      1 + marketQuote.percentChange24H
    ) - 1
    val percent_change_7d = BigDecimal(1 + tokenQuote.percentChange7D) / BigDecimal(
      1 + marketQuote.percentChange7D
    ) - 1
    val last_updated = marketQuote.lastUpdated
    Quote(
      price,
      volume_24h,
      toDouble(percent_change_1h),
      toDouble(percent_change_24h),
      toDouble(percent_change_7d),
      toDouble(market_cap),
      last_updated
    )
  }

  private def toDouble: PartialFunction[BigDecimal, Double] = {
    case s: BigDecimal =>
      scala.util
        .Try(s.setScale(8, BigDecimal.RoundingMode.HALF_UP).toDouble)
        .toOption
        .getOrElse(0)
  }

  private def convertToPersistence(
      tickers: Seq[CMCTickerData]
    ): Seq[TokenTickerInfo] = {
    tickers.flatMap { ticker =>
      val id = ticker.id
      val name = ticker.name
      val symbol = ticker.symbol
      val websiteSlug = ticker.slug
      val rank = ticker.cmcRank
      val circulatingSupply = ticker.circulatingSupply
      val totalSupply = ticker.totalSupply
      val maxSupply = ticker.maxSupply

      ticker.quote.map { priceQuote =>
        val market = priceQuote._1
        val quote = priceQuote._2
        val price = quote.price
        val volume24h = quote.volume24H
        val marketCap = quote.marketCap
        val percentChange1h = quote.percentChange1H
        val percentChange24h = quote.percentChange24H
        val percentChange7d = quote.percentChange7D
        val utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS Z")
        val lastUpdated = utcFormat
          .parse(quote.lastUpdated.replace("Z", " UTC"))
          .getTime / 1000
        val pair = symbol + "-" + market

        TokenTickerInfo(
          id,
          name,
          symbol,
          websiteSlug,
          market,
          rank,
          circulatingSupply,
          totalSupply,
          maxSupply,
          price,
          volume24h,
          marketCap,
          percentChange1h,
          percentChange24h,
          percentChange7d,
          lastUpdated,
          pair
        )
      }
    }
  }

  private def getMockedCMCTickers() = {
    import scala.io.Source
    val fileContents = Source.fromResource("cmc.data").getLines.mkString

    val res = parser.fromJsonString[TickerDataInfo](fileContents)
    res.status match {
      case Some(r) if r.errorCode == 0 =>
        Future.successful(res.copy(data = res.data.take(300)))
      case Some(r) if r.errorCode != 0 =>
        log.error(
          s"Failed request CMC, code:[${r.errorCode}] msg:[${r.errorMessage}]"
        )
        Future.successful(res)
      case m =>
        log.error(s"Failed request CMC, return:[$m]")
        Future.successful(Seq.empty)
    }
  }
}
