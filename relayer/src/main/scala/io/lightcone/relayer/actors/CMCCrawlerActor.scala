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
import io.lightcone.core.{ErrorCode, ErrorException, TokenMetadata}
import io.lightcone.lib._
import io.lightcone.persistence._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.external.{CurrencyManager, TickerManager}
import scala.concurrent.{ExecutionContext, Future}
import io.lightcone.relayer.jsonrpc._
import scala.util.{Failure, Success}

// Owner: Yongfeng
object CMCCrawlerActor extends DeployedAsSingleton {
  val name = "cmc_crawler"

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
      tickerManager: TickerManager,
      currencyManager: CurrencyManager,
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
    val tickerManager: TickerManager,
    val currencyManager: CurrencyManager,
    val system: ActorSystem)
    extends InitializationRetryActor
    with JsonSupport
    with RepeatedJobActor
    with ActorLogging {

  val metadataManagerActor = actors.get(MetadataManagerActor.name)

  val selfConfig = config.getConfig(CMCCrawlerActor.name)
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-delay-in-seconds")

  private var tickers: Seq[ThirdPartyTokenPrice] =
    Seq.empty[ThirdPartyTokenPrice]
  private var slugSymbols: Seq[CMCTokenSlug] = Seq.empty[CMCTokenSlug]

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
      latestEffectiveRequest <- dbModule.thirdPartyTokenPriceDal
        .getLatestEffectiveRequest()
      tickers_ <- if (latestEffectiveRequest.nonEmpty) {
        dbModule.thirdPartyTokenPriceDal.getTickersByRequestTime(
          latestEffectiveRequest.get
        )
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      if (tickers_ nonEmpty) {
        tickers = tickers_
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
      cmcResponse <- tickerManager.requestCMCTickers()
      rateResponse <- currencyManager.getUsdCnyCurrency()
      slugSymbols_ <- dbModule.cmcTokenSlugDal.getAll()
      updated <- if (cmcResponse.data.nonEmpty && rateResponse > 0) {
        for {
          _ <- persistTickers(rateResponse, cmcResponse.data)
          tokens <- dbModule.tokenMetadataDal.getTokens()
          _ <- updateTokenPrice(cmcResponse.data, tokens)
        } yield true
      } else {
        Future.successful(false)
      }
    } yield {
      slugSymbols = slugSymbols_
      if (updated) {
        metadataManagerActor ! ReloadMetadataFromDb()
      }
    }
  }

  private def updateTokenPrice(
      usdTickers: Seq[CMCTickerData],
      tokens: Seq[TokenMetadata]
    ) = {
    var changedTokens = Seq.empty[TokenMetadata]
    tokens.foreach { token =>
      val slugSymbolOpt = slugSymbols.find(t => t.symbol == token.symbol)
      if (slugSymbolOpt.isEmpty) {
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found slug for symbol: ${token.symbol}"
        )
      }

      val priceQuote =
        usdTickers
          .find(_.slug == slugSymbolOpt.get.slug)
          .flatMap(_.quote.get("USD"))
      val usdPriceQuote = priceQuote.getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"can not found slug:[${slugSymbolOpt.get.slug}] price in USD"
        )
      )
      val externalData = token.externalData
      if (externalData.get.usdPrice != usdPriceQuote.price) {
        changedTokens = changedTokens :+ token.copy(
          externalData =
            Some(externalData.get.copy(usdPrice = usdPriceQuote.price))
        )
      }
    }
    Future.sequence(changedTokens.map { token =>
      dbModule.tokenMetadataDal
        .updateTokenPrice(token.address, token.externalData.get.usdPrice)
        .map { r =>
          if (r != ErrorCode.ERR_NONE)
            log.error(s"failed to update token price:$token")
        }
    })
  }

  private def persistTickers(
      usdTocnyRate: Double,
      tickers_ : Seq[CMCTickerData]
    ) =
    for {
      _ <- Future.unit
      tickersToPersist = tickerManager.convertCMCResponseToPersistence(
        tickers_
      )
      cnyTicker = ThirdPartyTokenPrice(
        "rmb",
        Some(
          ThirdPartyTokenPrice.Ticker(
            price = tickerManager
              .toDouble(BigDecimal(1) / BigDecimal(usdTocnyRate))
          )
        )
      )
      now = timeProvider.getTimeSeconds()
      _ = tickers =
        tickersToPersist.+:(cnyTicker).map(t => t.copy(syncTime = now))
      fixGroup = tickersToPersist.grouped(20).toList
      _ <- Future.sequence(
        fixGroup.map(dbModule.thirdPartyTokenPriceDal.saveTickers)
      )
      updateSucc <- dbModule.thirdPartyTokenPriceDal.updateEffective(now)
    } yield {
      if (updateSucc != ErrorCode.ERR_NONE)
        log.error(s"CMC persist failed, code:$updateSucc")
    }

}
