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

package io.lightcone.relayer.support

import java.util.concurrent.TimeUnit
import io.lightcone.relayer.actors._
import io.lightcone.relayer.data._
import org.rnorth.ducttape.TimeoutException
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.containers.ContainerLaunchException
import akka.pattern._
import io.lightcone.core._
import io.lightcone.persistence.{CMCCrawlerConfigForToken, TokenTickerRecord}
import io.lightcone.relayer.external.CMCResponse.CMCTickerData
import io.lightcone.relayer.external._
import scalapb.json4s.Parser
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import io.lightcone.relayer.implicits._

trait MetadataManagerSupport extends DatabaseModuleSupport {
  me: CommonSpec =>

  val parser = new Parser(preservingProtoFieldNames = true) //protobuf 序列化为json不使用驼峰命名
  var tickers: Seq[TokenTickerRecord] = Seq.empty[TokenTickerRecord]

  val tokens = TOKENS.map { t =>
    Token(
      Some(t),
      Some(TokenInfo(symbol = t.symbol)),
      Some(TokenTicker(token = t.address, price = 0.1))
    )
  }

  val markets = MARKETS.map { m =>
    Market(
      Some(m),
      Some(
        MarketTicker(
          baseToken = m.marketPair.get.baseToken,
          quoteToken = m.marketPair.get.quoteToken,
          price = 0.0001
        )
      )
    )
  }
  metadataManager.reset(
    tokens,
    markets
  )

  val initialize = for {
    tokenTickers <- getMockedCMCTickers()
    tokenSymbolSlugs_ <- dbModule.cmcCrawlerConfigForTokenDal.getConfigs()
    tokenTickers_ = filterSlugTickers(tokenSymbolSlugs_, tokenTickers)
    currencyTickers <- fiatExchangeRateFetcher.fetchExchangeRates(
      CURRENCY_EXCHANGE_PAIR
    )
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
  } yield { log.info(s"External Tickers initialize done...") }
  Await.result(initialize.mapTo[Unit], 50.second)

  // actors.add(ExternalCrawlerActor.name, ExternalCrawlerActor.start)

  actors.add(MetadataManagerActor.name, MetadataManagerActor.start)
  try Unreliables.retryUntilTrue(
    10,
    TimeUnit.SECONDS,
    () => {
      val f =
        (actors.get(MetadataManagerActor.name) ? GetTokens.Req())
          .mapTo[GetTokens.Res]
      val res = Await.result(f, timeout.duration)
      res.tokens.nonEmpty
    }
  )
  catch {
    case e: TimeoutException =>
      throw new ContainerLaunchException(
        "Timed out waiting for MetadataManagerActor init.)"
      )
  }

  actors.add(MetadataRefresher.name, MetadataRefresher.start)
  try Unreliables.retryUntilTrue(
    10,
    TimeUnit.SECONDS,
    () => {
      val f = (actors.get(MetadataRefresher.name) ? GetTokens.Req())
        .mapTo[GetTokens.Res]
      val res = Await.result(f, timeout.duration)
      res.tokens.nonEmpty
      true
    }
  )
  catch {
    case e: TimeoutException =>
      throw new ContainerLaunchException(
        "Timed out waiting for MetadataRefresher init.)"
      )
  }

  private def filterSlugTickers(
      tokenSymbolSlugs: Seq[CMCCrawlerConfigForToken],
      tokenTickers: Seq[TokenTickerRecord]
    ) = {
    val slugMap = tokenSymbolSlugs.map(t => t.slug -> t.symbol).toMap
    val slugs = slugMap.keySet
    tokenTickers.filter(t => slugs.contains(t.slug)).map { t =>
      t.copy(symbol = slugMap(t.slug))
    }
  }

  private def getMockedCMCTickers(): Future[Seq[TokenTickerRecord]] = {
    import scala.io.Source
    val fileContents = Source.fromResource("cmc.data").getLines.mkString
    val res = parser.fromJsonString[CMCResponse](fileContents)
    res.status match {
      case Some(r) if r.errorCode == 0 =>
        Future.successful(fillTickersToPersistence(res.data))
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

  private def fillTickersToPersistence(tickersInUsd: Seq[CMCTickerData]) = {
    fillERC20TokenTickersToPersistence(tickersInUsd).++:(
      fillQuoteTickersToPersistence(tickersInUsd)
    )
  }

  private def fillERC20TokenTickersToPersistence(
      tickersInUsd: Seq[CMCTickerData]
    ): Seq[TokenTickerRecord] = {
    tickersInUsd
      .filter(
        t => t.platform.nonEmpty && t.platform.get.symbol == Currency.ETH.name
      )
      .map { t =>
        val q = getQuote(t)
        val p = t.platform.get
        normalize(
          TokenTickerRecord(
            p.tokenAddress,
            t.symbol,
            t.slug,
            q.price,
            q.volume24H,
            q.percentChange1H,
            q.percentChange24H,
            q.percentChange7D,
            q.marketCap,
            0,
            false,
            "CMC"
          )
        )
      }
  }

  private def normalize(record: TokenTickerRecord) = {
    record.copy(
      tokenAddress = record.tokenAddress.toLowerCase,
      symbol = record.symbol.toUpperCase
    )
  }

  private def getQuote(ticker: CMCTickerData) = {
    if (ticker.quote.get("USD").isEmpty) {
      log.error(s"CMC not return ${ticker.symbol} quote for USD")
      throw ErrorException(
        ErrorCode.ERR_INTERNAL_UNKNOWN,
        s"CMC not return ${ticker.symbol} quote for USD"
      )
    }
    ticker.quote("USD")
  }

  private def fillQuoteTickersToPersistence(
      tickersInUsd: Seq[CMCTickerData]
    ) = {
    QUOTE_TOKEN.map { t =>
      val ticker = tickersInUsd
        .find(u => u.symbol == t && u.platform.isEmpty)
        .getOrElse(
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"not found ticker for token: $t"
          )
        )
      val quote = getQuote(ticker)
      val currency = Currency
        .fromName(t)
        .getOrElse(
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"not found Currency of name:$t"
          )
        )
      normalize(
        TokenTickerRecord(
          currency.getAddress(),
          t,
          ticker.slug,
          quote.price,
          quote.volume24H,
          quote.percentChange1H,
          quote.percentChange24H,
          quote.percentChange7D,
          quote.marketCap,
          0,
          false,
          "CMC"
        )
      )
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
}
