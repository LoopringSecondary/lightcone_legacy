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
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.core.MetadataManager
import io.lightcone.lib._
import io.lightcone.relayer.base._
import io.lightcone.persistence._
import io.lightcone.relayer.data._
import io.lightcone.relayer.external.TickerManager
import io.lightcone.relayer.rpc.{ExternalTickerInfo, GetExternalTickers}
import scala.concurrent._

// Owner: YongFeng
object ExternalDataRefresher {
  val name = "external_data_refresher"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      metadataManager: MetadataManager,
      tickerManager: TickerManager
    ) = {
    system.actorOf(
      Props(new ExternalDataRefresher()),
      ExternalDataRefresher.name
    )
  }
}

// main owner: YongFeng
class ExternalDataRefresher(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val metadataManager: MetadataManager,
    val tickerManager: TickerManager,
    val dbModule: DatabaseModule)
    extends Actor
    with RepeatedJobActor
    with ActorLogging {
  def tokenTickerCrawlerActor = actors.get(CMCCrawlerActor.name)
  def currencyRateCrawlerActor = actors.get(CurrencyCrawlerActor.name)

  val selfConfig = config.getConfig(ExternalDataRefresher.name)
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-delay-in-seconds")

  private val supportMarketSymbols = metadataManager.getSupportMarketSymbols

  private var tickersInUSD: Seq[ExternalTickerInfo] =
    Seq.empty[ExternalTickerInfo]
  private var tickersInCNY: Seq[ExternalTickerInfo] =
    Seq.empty[ExternalTickerInfo]
  private var currencyRate: Option[CurrencyRate] = None

  val repeatedJobs = Seq(
    Job(
      name = "sync_external_datas",
      dalayInSeconds = refreshIntervalInSeconds,
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => refreshAll()
    )
  )

  def receive: Receive = super.receiveRepeatdJobs orElse {
    case req: GetExternalTickers.Req =>
      val tickerSource =
        if (req.currency == GetExternalTickers.Currency.CNY) tickersInCNY
        else tickersInUSD
      val tickers_ = if (req.market.isEmpty) {
        tickerSource
      } else {
        tickerSource.filter(_.market == req.market)
      }
      val rate = if (currencyRate.nonEmpty) currencyRate.get.rate else 0
      sender ! GetExternalTickers.Res(
        req.currency,
        rate,
        tickers_
      )
  }

  private def refreshAll() =
    for {
      _ <- refreshTickers()
      _ <- refreshCurrencyRate()
    } yield {}

  private def refreshTickers() =
    for {
      tickers_ <- (tokenTickerCrawlerActor ? GetTickers.Req())
        .mapTo[GetTickers.Res]
        .map(_.tickers)
    } yield {
      assert(tickers_.nonEmpty)
      tickersInUSD = tickerManager.convertPersistenceToAllSupportMarkets(
        tickers_,
        supportMarketSymbols
      )
      tickersInCNY =
        tickerManager.convertUsdTickersToCny(tickersInUSD, currencyRate)
    }

  private def refreshCurrencyRate() = {
    for {
      rate_ <- (currencyRateCrawlerActor ? GetCurrencyRate.Req())
        .mapTo[GetCurrencyRate.Res]
        .map(_.rate)
    } yield {
      assert(rate_.nonEmpty && rate_.get.rate > 0)
      currencyRate = rate_
    }
  }

}
