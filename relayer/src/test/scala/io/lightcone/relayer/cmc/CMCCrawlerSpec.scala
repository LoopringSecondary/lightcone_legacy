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

package io.lightcone.relayer.cmc

import io.lightcone.core._
import io.lightcone.persistence._
import io.lightcone.relayer.actors._
import io.lightcone.relayer.external.CMCResponse.CMCTickerData
import io.lightcone.relayer.external._
import io.lightcone.relayer.support._
import io.lightcone.relayer.implicits._
import io.lightcone.relayer.data.{GetMarkets, GetTokens}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class CMCCrawlerSpec
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with EthereumSupport
    with DatabaseModuleSupport
    with MetadataManagerSupport {

  val metadataManagerActor = actors.get(MetadataManagerActor.name)

  "cmc crawler" must {
    "sina currency rate" in {
      val r =
        Await.result(
          fiatExchangeRateFetcher
            .fetchExchangeRates(CURRENCY_EXCHANGE_PAIR)
            .mapTo[Seq[TokenTickerRecord]],
          5.second
        )
      r.nonEmpty should be(true)
      val map = r.map(t => t.symbol -> t.price).toMap
      map.contains(Currency.RMB.name) should be(true)
      map(Currency.RMB.name) > 0 should be(true)
      map.contains(Currency.JPY.name) should be(true)
      map(Currency.JPY.name) > 0 should be(true)
      map.contains(Currency.EUR.name) should be(true)
      map(Currency.EUR.name) > 0 should be(true)
      map.contains(Currency.GBP.name) should be(true)
      map(Currency.GBP.name) > 0 should be(true)
    }

    "request cmc tickers in USD and persist (CMCCrawlerActor)" in {
      val f = for {
        tokenTickers <- getMockedCMCTickers()
        _ = assert(tokenTickers.nonEmpty)
        currencyTickers <- fiatExchangeRateFetcher.fetchExchangeRates(
          CURRENCY_EXCHANGE_PAIR
        )
        _ = assert(currencyTickers.nonEmpty)
        persistTickers <- persistTickers(
          currencyTickers,
          tokenTickers
        )
      } yield (tokenTickers, currencyTickers, persistTickers)
      val q1 = Await.result(
        f.mapTo[
          (
              Seq[TokenTickerRecord],
              Seq[TokenTickerRecord],
              Seq[TokenTickerRecord]
          )
        ],
        50.second
      )
      tickers = q1._3
      q1._1.length should be(1099)
      q1._2.length should be(CURRENCY_EXCHANGE_PAIR.length)
      q1._3.length should be(1099 + CURRENCY_EXCHANGE_PAIR.length)
    }

    "getTokens require [metadata]" in {
      val f1 = singleRequest(GetTokens.Req(true), "get_tokens")
      val res1 = Await.result(f1.mapTo[GetTokens.Res], timeout.duration)
      res1.tokens.nonEmpty should be(true)
      res1.tokens.foreach { t =>
        t.metadata.nonEmpty should be(true)
        t.info.isEmpty should be(true)
        t.price should be(0.0)
      }
    }

    "getTokens require [metadata] with token address" in {
      val f1 = singleRequest(
        GetTokens.Req(requireMetadata = true, tokens = Seq(LRC_TOKEN.address)),
        "get_tokens"
      )
      val res1 = Await.result(f1.mapTo[GetTokens.Res], timeout.duration)
      res1.tokens.length == 1 should be(true)
      val lrc = res1.tokens.head
      lrc.metadata.nonEmpty should be(true)
      lrc.info.isEmpty should be(true)
      lrc.price should be(0.0)
    }

    "getTokens require [metadata, info]" in {
      val f1 = singleRequest(GetTokens.Req(true, true), "get_tokens")
      val res1 = Await.result(f1.mapTo[GetTokens.Res], timeout.duration)
      res1.tokens.nonEmpty should be(true)
      res1.tokens.foreach { t =>
        t.metadata.nonEmpty should be(true)
        t.info.nonEmpty should be(true)
        t.price should be(0.0)
      }
    }

    "getTokens require [metadata, info, ticker]" in {
      val f1 = singleRequest(GetTokens.Req(true, true, true), "get_tokens")
      val res1 = Await.result(f1.mapTo[GetTokens.Res], timeout.duration)
      res1.tokens.nonEmpty should be(true)
      res1.tokens.foreach { t =>
        t.metadata.nonEmpty should be(true)
        t.info.nonEmpty should be(true)
        t.price > 0 should be(true)
      }
    }

    "getTokens require [metadata, info, ticker] , with quote [ETH, RMB]" in {
      val f1 = singleRequest(GetTokens.Req(true, true, true), "get_tokens")
      val res1 = Await.result(f1.mapTo[GetTokens.Res], timeout.duration)
      res1.tokens.nonEmpty should be(true)
      res1.tokens.foreach { t =>
        t.metadata.nonEmpty should be(true)
        t.info.nonEmpty should be(true)
        t.price > 0 should be(true)
      }
      val f2 = singleRequest(
        GetTokens.Req(true, true, true, Currency.ETH),
        "get_tokens"
      )
      val res2 = Await.result(f2.mapTo[GetTokens.Res], timeout.duration)
      res2.tokens.nonEmpty should be(true)
      res2.tokens.foreach { t =>
        t.metadata.nonEmpty should be(true)
        t.info.nonEmpty should be(true)
        t.price > 0 should be(true)
      }

      val f3 = singleRequest(
        GetTokens.Req(true, true, true, Currency.RMB),
        "get_tokens"
      )
      val res3 = Await.result(f3.mapTo[GetTokens.Res], timeout.duration)
      res3.tokens.nonEmpty should be(true)
      res3.tokens.foreach { t =>
        t.metadata.nonEmpty should be(true)
        t.info.nonEmpty should be(true)
        t.price > 0 should be(true)
      }

      val lrc1 = res1.tokens.find(_.getMetadata.symbol == LRC_TOKEN.symbol)
      lrc1.nonEmpty should be(true)
      val lrc2 = res2.tokens.find(_.getMetadata.symbol == LRC_TOKEN.symbol)
      lrc2.nonEmpty should be(true)
      val lrc3 = res3.tokens.find(_.getMetadata.symbol == LRC_TOKEN.symbol)
      lrc3.nonEmpty should be(true)
      lrc1 != lrc2 && lrc1 != lrc3 && lrc2 != lrc3 should be(true)
    }

    "getMarkets require [metadata]" in {
      val f1 = singleRequest(GetMarkets.Req(true), "get_markets")
      val res1 = Await.result(f1.mapTo[GetMarkets.Res], timeout.duration)
      res1.markets.nonEmpty should be(true)
      res1.markets.foreach { m =>
        m.metadata.nonEmpty should be(true)
        m.ticker.isEmpty should be(true)
      }
    }

    "getMarkets require [metadata] with market pair" in {
      val lrcWeth = MarketPair(LRC_TOKEN.address, WETH_TOKEN.address)
      val f1 = singleRequest(
        GetMarkets.Req(requireMetadata = true, marketPairs = Seq(lrcWeth)),
        "get_markets"
      )
      val res1 = Await.result(f1.mapTo[GetMarkets.Res], timeout.duration)
      res1.markets.length should be(1)
      val m = res1.markets.head
      m.metadata.nonEmpty should be(true)
      m.ticker.isEmpty should be(true)
    }

    "getMarkets require [metadata, ticker]" in {
      val f1 = singleRequest(GetMarkets.Req(true, true), "get_markets")
      val res1 = Await.result(f1.mapTo[GetMarkets.Res], timeout.duration)
      res1.markets.nonEmpty should be(true)
      res1.markets.foreach { m =>
        m.metadata.nonEmpty should be(true)
        m.ticker.nonEmpty should be(true)
        m.ticker.get.price > 0 should be(true)
      }
    }

    "getMarkets require [metadata, ticker], with quote [BTC, GBP]" in {
      val f1 = singleRequest(GetMarkets.Req(true, true), "get_markets")
      val res1 = Await.result(f1.mapTo[GetMarkets.Res], timeout.duration)
      res1.markets.nonEmpty should be(true)
      res1.markets.foreach { m =>
        m.metadata.nonEmpty should be(true)
        m.ticker.nonEmpty should be(true)
        m.ticker.get.price > 0 should be(true)
      }

      val f2 = singleRequest(
        GetMarkets.Req(true, true, false, Currency.BTC),
        "get_markets"
      )
      val res2 = Await.result(f2.mapTo[GetMarkets.Res], timeout.duration)
      res2.markets.nonEmpty should be(true)
      res2.markets.foreach { m =>
        m.metadata.nonEmpty should be(true)
        m.ticker.nonEmpty should be(true)
        m.ticker.get.price > 0 should be(true)
      }

      val f3 = singleRequest(
        GetMarkets.Req(true, true, false, Currency.GBP),
        "get_markets"
      )
      val res3 = Await.result(f3.mapTo[GetMarkets.Res], timeout.duration)
      res3.markets.nonEmpty should be(true)
      res3.markets.foreach { m =>
        m.metadata.nonEmpty should be(true)
        m.ticker.nonEmpty should be(true)
        m.ticker.get.price > 0 should be(true)
      }

      val lrcWethHash =
        MarketHash(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))
          .hashString()
      val lrcWeth1 = res1.markets.find(_.getMetadata.marketHash == lrcWethHash)
      lrcWeth1.nonEmpty should be(true)
      val lrcWeth2 = res2.markets.find(_.getMetadata.marketHash == lrcWethHash)
      lrcWeth2.nonEmpty should be(true)
      val lrcWeth3 = res3.markets.find(_.getMetadata.marketHash == lrcWethHash)
      lrcWeth3.nonEmpty should be(true)
      lrcWeth1 != lrcWeth2 && lrcWeth1 != lrcWeth3 && lrcWeth2 != lrcWeth3 should be(
        true
      )
    }
  }

  private def getMockedCMCTickers() = {
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

  def fillTickersToPersistence(tickersInUsd: Seq[CMCTickerData]) = {
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

}
