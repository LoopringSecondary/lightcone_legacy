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
import io.lightcone.persistence.TokenTickerRecord
import io.lightcone.relayer.actors._
import io.lightcone.relayer.external.CMCResponse.CMCTickerData
import io.lightcone.relayer.external._
import io.lightcone.relayer.support._
import scalapb.json4s.Parser
import io.lightcone.relayer.implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class CMCCrawlerSpec
    extends CommonSpec
    with HttpSupport
    with EthereumSupport
    with DatabaseModuleSupport
    with MetadataManagerSupport {

  val metadataManagerActor = actors.get(MetadataManagerActor.name)

  val parser = new Parser(preservingProtoFieldNames = true) //protobuf 序列化为json不使用驼峰命名

  var tickers: Seq[TokenTicker] = Seq.empty[TokenTicker]

  private val tokens = metadataManager.getTokens
  private val effectiveMarkets = metadataManager
    .getMarkets()
    .filter(_.status != MarketMetadata.Status.TERMINATED)

  private var tickerRecords: Seq[TokenTickerRecord] =
    Seq.empty[TokenTickerRecord]
  private var tokenTickersInUSD: Seq[TokenTicker] =
    Seq.empty[TokenTicker] // USD price
  private var marketTickers: Seq[MarketTicker] =
    Seq.empty[MarketTicker] // price represent exchange rate of market (price of market LRC-WETH is 0.01)

  "cmc crawler" must {
    "sina currency rate" in {
      val r =
        Await.result(
          fiatExchangeRateFetcher
            .fetchExchangeRates(CURRENCY_EXCHANGE_PAIR)
            .mapTo[Map[String, Double]],
          5.second
        )
      r.nonEmpty should be(true)
      r.contains(USD_RMB) should be(true)
      r(USD_RMB) > 0 should be(true)
      r.contains(USD_JPY) should be(true)
      r(USD_JPY) > 0 should be(true)
      r.contains(USD_EUR) should be(true)
      r(USD_EUR) > 0 should be(true)
      r.contains(USD_GBP) should be(true)
      r(USD_GBP) > 0 should be(true)
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
      tickerRecords = q1._3
      q1._1.length should be(tokens.length)
      q1._2.length should be(Currency.values.length)
      q1._3.length should be(tokens.length + Currency.values.length)
    }

    "convert USD tickers to all quote markets (ExternalDataRefresher)" in {
      refreshTickers()

      // get a random position
      assert(tokenTickersInUSD.length == tokens.length)
      val p = (new util.Random).nextInt(tokenTickersInUSD.size)
      val tickerInUsd = tokenTickersInUSD(p)
      assert(tickerInUsd.price > 0)
      assert(tickerInUsd.volume24H > 0)
      assert(tickerInUsd.symbol.nonEmpty)
      assert(tickerInUsd.percentChange1H > 0)
      assert(tickerInUsd.percentChange24H > 0)
      assert(tickerInUsd.percentChange7D > 0)
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

  private def refreshTickers() = this.synchronized {
    //    val cnyToUsd = tickerRecords.find(_.tokenAddress == Currency.RMB.getAddress())
    //    assert(cnyToUsd.nonEmpty)
    //    assert(cnyToUsd.get.price > 0)
    // except currency and quote tokens
    val effectiveTokens = tickerRecords.filter(isEffectiveToken)
    tokenTickersInUSD = effectiveTokens
      .map(convertPersistToExternal)
    marketTickers = fillAllMarketTickers(effectiveTokens, effectiveMarkets)
  }

  private def isEffectiveToken(ticker: TokenTickerRecord): Boolean = {
    tokens.exists(_.metadata.get.address == ticker.tokenAddress)
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
        TokenTickerRecord(
          p.tokenAddress,
          t.symbol,
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
      }
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
      new TokenTickerRecord(
        symbol = t,
        tokenAddress = currency.getAddress(),
        price = quote.price,
        dataSource = "CMC"
      )
    }
  }

}
