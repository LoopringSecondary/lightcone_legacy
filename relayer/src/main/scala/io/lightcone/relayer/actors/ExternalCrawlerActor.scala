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
    val system: ActorSystem)
    extends InitializationRetryActor
    with JsonSupport
    with RepeatedJobActor
    with ActorLogging {

  @inline def metadataManagerActor = actors.get(MetadataManagerActor.name)

  val cmcExternalTickerFetcher = new CMCExternalTickerFetcher()
  val sinaFiatExchangeRateFetcher = new SinaFiatExchangeRateFetcher()
  val exchangeRateAPIFetcher = new ExchangeRateAPIFetcher()

  val selfConfig = config.getConfig(ExternalCrawlerActor.name)
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-delay-in-seconds")

  private var tickers: Seq[TokenTickerRecord] = Seq.empty[TokenTickerRecord]

  val repeatedJobs = Seq(
    Job(
      name = "sync_external_datas",
      dalayInSeconds = refreshIntervalInSeconds,
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => syncTickers()
    )
  )

  def ready: Receive = super.receiveRepeatdJobs

  private def syncTickers() = this.synchronized {
    log.info("ExternalCrawlerActor run sync job")
    for {
      tokenTickers_ <- cmcExternalTickerFetcher.fetchExternalTickers()
      currencyTickers <- syncCurrencyTicker()
      persistTickers <- if (tokenTickers_.nonEmpty && currencyTickers.nonEmpty) {
        persistTickers(
          currencyTickers,
          tokenTickers_
        )
      } else {
        if (tokenTickers_.nonEmpty) log.error("failed request CMC tickers")
        if (currencyTickers.nonEmpty)
          log.error("failed request Sina currency rate")
        Future.successful(Seq.empty)
      }
    } yield {
      assert(persistTickers.nonEmpty)
      tickers = persistTickers
      notifyChanged()
    }
  }

  private def syncCurrencyTicker() = {
    sinaFiatExchangeRateFetcher.fetchExchangeRates() recoverWith {
      case e: Exception =>
        log.error(s"Failed request Sina currency rate cause [${e.getMessage}], trying to request exchangerate-api")
        exchangeRateAPIFetcher.fetchExchangeRates()
    }
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
      updatedValid <- dbModule.tokenTickerRecordDal.setValid(now)
    } yield {
      if (updatedValid != ErrorCode.ERR_NONE)
        log.error(s"External tickers persist failed, code:$updatedValid")
      tickers_
    }

  private def notifyChanged() = {
    metadataManagerActor ! MetadataChanged(false, false, false, true)
  }

}
