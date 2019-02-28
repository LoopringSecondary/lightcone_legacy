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
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.persistence._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.external._
import scala.concurrent.{ExecutionContext, Future}
import io.lightcone.relayer.jsonrpc._
import scala.util.{Failure, Success}
import io.lightcone.relayer.implicits._

// Owner: Yongfeng
object ExternalCrawlerActor extends DeployedAsSingleton {
  val name = "external_crawler"
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
      externalTickerFetcher: ExternalTickerFetcher,
      fiatExchangeRateFetcher: FiatExchangeRateFetcher,
      metadataManager: MetadataManager,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new ExternalCrawlerActor()))
  }
}

class ExternalCrawlerActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val materializer: ActorMaterializer,
    val dbModule: DatabaseModule,
    val externalTickerFetcher: ExternalTickerFetcher,
    val fiatExchangeRateFetcher: FiatExchangeRateFetcher,
    val metadataManager: MetadataManager,
    val system: ActorSystem)
    extends InitializationRetryActor
    with JsonSupport
    with RepeatedJobActor
    with ActorLogging {

  private def metadataManagerActor = actors.get(MetadataManagerActor.name)

  val selfConfig = config.getConfig(ExternalCrawlerActor.name)
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-delay-in-seconds")

  private val tokens = metadataManager.getTokens()
  private val effectiveMarketMetadatas = metadataManager
    .getMarkets()
    .filter(_.metadata.get.status != MarketMetadata.Status.TERMINATED)
    .map(_.metadata.get)

  private var tokenTickersInUSD: Seq[TokenTicker] =
    Seq.empty[TokenTicker] // USD price
  private var marketTickers: Seq[MarketTicker] =
    Seq.empty[MarketTicker] // price represent exchange rate of market (price of market LRC-WETH is 0.01)

  val repeatedJobs = Seq(
    Job(
      name = "sync_external_datas",
      dalayInSeconds = refreshIntervalInSeconds,
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => syncTickers()
    )
  )

  override def initialize() = {
    val f = for {
      latestEffectiveTime <- dbModule.tokenTickerRecordDal
        .getLastTicker()
      tickers_ <- if (latestEffectiveTime.nonEmpty) {
        dbModule.tokenTickerRecordDal.getTickers(
          latestEffectiveTime.get
        )
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      if (tickers_.nonEmpty) {
        refreshTickers(tickers_)
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
    case _: LoadTickers.Req =>
      sender ! LoadTickers.Res(tokenTickersInUSD, marketTickers)
  }

  private def syncTickers() = this.synchronized {
    log.info("ExternalCrawlerActor run sync job")
    for {
      tokenTickers <- externalTickerFetcher.fetchExternalTickers()
      _ = assert(tokenTickers.nonEmpty)
      currencyTickers <- fiatExchangeRateFetcher.fetchExchangeRates(
        CURRENCY_EXCHANGE_PAIR
      )
      _ = assert(currencyTickers.nonEmpty)
      persistTickers <- persistTickers(
        currencyTickers,
        tokenTickers
      )
    } yield {
      assert(persistTickers.nonEmpty)

      refreshTickers(persistTickers)
      metadataManagerActor ! TokenTickerChanged(
        tokenTickersInUSD,
        marketTickers
      )
    }
  }

  private def refreshTickers(tickerRecords: Seq[TokenTickerRecord]) =
    this.synchronized {
//    val cnyToUsd = tickerRecords.find(_.tokenAddress == Currency.RMB.getAddress())
//    assert(cnyToUsd.nonEmpty)
//    assert(cnyToUsd.get.price > 0)
      // except currency and quote tokens
      val effectiveTokens = tickerRecords.filter(isEffectiveToken)
      tokenTickersInUSD = effectiveTokens
        .map(convertPersistToExternal)
      marketTickers =
        fillAllMarketTickers(effectiveTokens, effectiveMarketMetadatas)
    }

  private def isEffectiveToken(ticker: TokenTickerRecord): Boolean = {
    tokens
      .map(_.metadata.get.address)
      .:+(Currency.values.map(_.getAddress()))
      .contains(ticker.tokenAddress)
  }

  private def persistTickers(
      currencyTickersInUsd: Seq[TokenTickerRecord],
      tokenTickersInUsd: Seq[TokenTickerRecord]
    ) =
    for {
      _ <- Future.unit
      now = timeProvider.getTimeSeconds()
      tickers_ = tokenTickersInUsd
        .++:(currencyTickersInUsd)
        .map(_.copy(timestamp = now))
      fixGroup = tickers_.grouped(20).toList
      _ <- Future.sequence(
        fixGroup.map(dbModule.tokenTickerRecordDal.saveTickers)
      )
      updateSucc <- dbModule.tokenTickerRecordDal.setValid(now)
    } yield {
      if (updateSucc != ErrorCode.ERR_NONE)
        log.error(s"External tickers persist failed, code:$updateSucc")
      tickers_
    }

  private def convertPersistToExternal(ticker: TokenTickerRecord) = {
    TokenTicker(
      ticker.symbol,
      ticker.price,
      ticker.volume24H,
      ticker.percentChange1H,
      ticker.percentChange24H,
      ticker.percentChange7D
    )
  }

  private def fillAllMarketTickers(
      usdTickers: Seq[TokenTickerRecord],
      effectiveMarket: Seq[MarketMetadata]
    ): Seq[MarketTicker] = {
    effectiveMarket.map(m => calculateMarketQuote(m, usdTickers))
  }

  private def calculateMarketQuote(
      market: MarketMetadata,
      usdTickers: Seq[TokenTickerRecord]
    ): MarketTicker = {
    val pair = market.marketPair.get
    val baseTicker = getTickerByAddress(pair.baseToken, usdTickers)
    val quoteTicker = getTickerByAddress(pair.quoteToken, usdTickers)
    val rate = toDouble(BigDecimal(baseTicker.price / quoteTicker.price))
    val volume24H = toDouble(
      BigDecimal(baseTicker.volume24H / baseTicker.price) * rate
    )
    val market_cap = toDouble(
      BigDecimal(baseTicker.marketCap / baseTicker.price) * rate
    )
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

  private def getTickerByAddress(
      address: String,
      usdTickers: Seq[TokenTickerRecord]
    ) = {
    usdTickers
      .find(t => t.tokenAddress == address)
      .getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found ticker of address: $address"
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

  private def convertUsdTickersToCny(
      usdTickers: Seq[TokenTicker],
      usdToCny: Option[TokenTickerRecord]
    ) = {
    if (usdTickers.nonEmpty && usdToCny.nonEmpty) {
      val cnyToUsd = usdToCny.get.price
      usdTickers.map { t =>
        t.copy(
          price = toDouble(BigDecimal(t.price) / BigDecimal(cnyToUsd)),
          volume24H = toDouble(BigDecimal(t.volume24H) / BigDecimal(cnyToUsd))
        )
      }
    } else {
      Seq.empty
    }
  }

}
