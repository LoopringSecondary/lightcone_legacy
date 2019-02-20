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
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.relayer.base._
import io.lightcone.persistence._
import io.lightcone.relayer.data._
import io.lightcone.relayer.external.TickerManager
import io.lightcone.relayer.rpc._
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
      tickerManager: TickerManager,
      materializer: ActorMaterializer
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
    val materializer: ActorMaterializer,
    val dbModule: DatabaseModule,
    val tickerManager: TickerManager,
    val system: ActorSystem)
    extends Actor
    with RepeatedJobActor
    with ActorLogging {
  def tokenTickerCrawlerActor = actors.get(CMCCrawlerActor.name)

  val selfConfig = config.getConfig(ExternalDataRefresher.name)
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-delay-in-seconds")

  private val marketQuoteTokens = metadataManager.getMarketQuoteTokens
  private val effectiveMarketSymbols = metadataManager
    .getMarkets()
    .filter(_.status != MarketMetadata.Status.TERMINATED)
    .map(m => (m.baseTokenSymbol, m.quoteTokenSymbol))

  private var allTickersInUSD: Seq[ExternalTickerInfo] =
    Seq.empty[ExternalTickerInfo] // price represent token's fait value in USD
  private var allTickersInCNY: Seq[ExternalTickerInfo] =
    Seq.empty[ExternalTickerInfo] // price represent token's fait value in CNY
  private var effectiveMarketTickers: Seq[ExternalTickerInfo] =
    Seq.empty[ExternalTickerInfo] // price represent exchange rate of market (price of market LRC-WETH is 0.01)

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
      val tickers_ = if (req.market.isEmpty) {
        effectiveMarketTickers
      } else {
        effectiveMarketTickers.filter(_.market == req.market)
      }
      sender ! GetExternalTickers.Res(
        tickers_
      )
  }

  private def refreshAll() =
    for {
      _ <- refreshTickers()
    } yield {}

  private def refreshTickers() =
    for {
      tickers_ <- (tokenTickerCrawlerActor ? GetTickers.Req())
        .mapTo[GetTickers.Res]
        .map(_.tickers)
    } yield {
      assert(tickers_.nonEmpty)
      val cnyToUsd =
        tickers_.find(t => t.symbol == "CNY" && t.slug == "rmb")
      assert(cnyToUsd.nonEmpty)
      assert(cnyToUsd.get.usdQuote.nonEmpty)
      assert(cnyToUsd.get.usdQuote.get.price > 0)
      allTickersInUSD = tickers_.map(tickerManager.convertPersistToExternal)
      allTickersInCNY = tickers_.map { t =>
        val t_ = tickerManager.convertPersistToExternal(t)
        assert(t.usdQuote.nonEmpty)
        t_.copy(
          price = tickerManager.toDouble(
            BigDecimal(t.usdQuote.get.price) * BigDecimal(
              cnyToUsd.get.usdQuote.get.price
            )
          )
        )
      }
      effectiveMarketTickers = tickerManager
        .convertPersistenceToAllQuoteMarkets(
          tickers_,
          marketQuoteTokens
        )
        .filter(isEffectiveMarket)
    }

  private def isEffectiveMarket(ticker: ExternalTickerInfo): Boolean = {
    effectiveMarketSymbols.contains((ticker.symbol, ticker.market))
  }

}