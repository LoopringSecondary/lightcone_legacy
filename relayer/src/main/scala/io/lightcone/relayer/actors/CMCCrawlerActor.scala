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
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.cmc._
import io.lightcone.core.{ErrorCode, ErrorException, TokenMetadata}
import io.lightcone.lib._
import io.lightcone.persistence._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import io.lightcone.relayer.jsonrpc._
import scalapb.json4s.Parser
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
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new CMCCrawlerActor()))
  }

  def normalizeTicker(ticker: CMCTickerData): CMCTickerData =
    ticker.copy(
      symbol = ticker.symbol.toUpperCase()
    )
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
    val system: ActorSystem)
    extends InitializationRetryActor
    with JsonSupport
    with RepeatedJobActor
    with ActorLogging {

  val selfConfig = config.getConfig(CMCCrawlerActor.name)
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-dalay-in-seconds")

  val mock = selfConfig.getBoolean("request.mock")
  val requestHeader = selfConfig.getString("request.header")
  val apiKey = selfConfig.getString("request.api-key")
  val prefixUrl = selfConfig.getString("request.prefix-url")
  val limitSize = selfConfig.getString("request.limit-size")
  val convertCurrency = selfConfig.getString("request.convert-currency")

  val uri =
    s"$prefixUrl/v1/cryptocurrency/listings/latest?start=1&limit=${limitSize}&convert=${convertCurrency}"
  val rawHeader = RawHeader(requestHeader, apiKey)
  val parser = new Parser(preservingProtoFieldNames = true) //protobuf 序列化为json不使用驼峰命名

  val mediator = DistributedPubSub(context.system).mediator

  val markets =
    selfConfig.getStringList("markets").asScala.toSeq

  private var tickers: Seq[TokenTickerInfo] = Seq.empty[TokenTickerInfo]

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
      tickers_ <- dbModule.tokenTickerInfoDal.getAll()
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
    case _: GetTokenTickers.Req =>
      sender ! GetTokenTickers.Res(tickers)
  }

  private def syncFromCMC() = this.synchronized {
    log.info("CMCCrawlerActor run sync job")
    for {
      usdTickers <- getCMCTickers()
      marketTickers = getMarketUSDQuote(usdTickers)
      tickersWithAllSupportMarket = fillInAllSupportMarkets(
        usdTickers,
        marketTickers
      )
      _ <- updateTickers(tickersWithAllSupportMarket)
      tokens <- dbModule.tokenMetadataDal.getTokens()
      _ <- updateTokenPrice(usdTickers, tokens)
    } yield {
      publish()
    }
  }

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

  private def updateTickers(tickersToUpdate: Seq[CMCTickerData]) =
    for {
      _ <- Future.unit
      _ = tickers = Seq.empty
      fixGroup = tickersToUpdate.grouped(20).toList
      _ = fixGroup.map { group =>
        val batchTickers = convertToPersistence(group)
        tickers ++= batchTickers
        dbModule.tokenTickerInfoDal.saveOrUpdate(batchTickers)
      }
    } yield Unit

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

  private def requestCMCTickers(
    )(
      implicit
      system: ActorSystem,
      ec: ExecutionContext
    ) = {
    for {
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.GET,
          uri = Uri(uri)
        ).withHeaders(rawHeader)
      )
      res <- response match {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          entity.dataBytes
            .map(_.utf8String)
            .runReduce(_ + _)
            .map(parser.fromJsonString[TickerDataInfo])
            .map { j =>
              j.status match {
                case Some(r) if r.errorCode == 0 =>
                  j.data.map(CMCCrawlerActor.normalizeTicker)
                case Some(r) if r.errorCode != 0 =>
                  log.error(
                    s"Failed request CMC, code:[${r.errorCode}] msg:[${r.errorMessage}]"
                  )
                  Seq.empty
                case m =>
                  log.error(s"Failed request CMC, return:[$m]")
                  Seq.empty
              }
            }

        case m =>
          log.error(s"get ticker data from coinmarketcap failed:$m")
          Future.successful(Seq.empty)
      }
    } yield res
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
      case Some(r) if r.errorCode == 0 => Future.successful(res.data.take(300))
      case Some(r) if r.errorCode != 0 =>
        log.error(
          s"Failed request CMC, code:[${r.errorCode}] msg:[${r.errorMessage}]"
        )
        Future.successful(Seq.empty)
      case m =>
        log.error(s"Failed request CMC, return:[$m]")
        Future.successful(Seq.empty)
    }
  }

  private def getCMCTickers() = {
    if (mock) getMockedCMCTickers()
    else requestCMCTickers()
  }
}
