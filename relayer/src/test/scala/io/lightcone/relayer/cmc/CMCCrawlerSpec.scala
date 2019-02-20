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
import io.lightcone.external._
import io.lightcone.persistence._
import io.lightcone.relayer.actors._
import io.lightcone.relayer.external.{CurrencyManager, SinaCurrencyManagerImpl}
import io.lightcone.relayer.rpc.ExternalTickerInfo
import io.lightcone.relayer.support._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scalapb.json4s.Parser

class CMCCrawlerSpec
    extends CommonSpec
    with HttpSupport
    with EthereumSupport
    with DatabaseModuleSupport
    with MetadataManagerSupport {

  val metadataManagerActor = actors.get(MetadataManagerActor.name)

  val parser = new Parser(preservingProtoFieldNames = true) //protobuf 序列化为json不使用驼峰命名

  var tickers: Seq[CMCTickersInUsd] = Seq.empty[CMCTickersInUsd]

  private val tokens = metadataManager.getTokens
  private val marketQuoteTokens = metadataManager.getMarketQuoteTokens()
  private val effectiveMarketSymbols = metadataManager
    .getMarkets()
    .filter(_.status != MarketMetadata.Status.TERMINATED)
    .map(m => (m.baseTokenSymbol, m.quoteTokenSymbol))

  "cmc crawler" must {
    "sina currency rate" in {
      val manager: CurrencyManager = new SinaCurrencyManagerImpl()
      val r =
        Await.result(manager.getUsdCnyCurrency().mapTo[Double], 5.second)
      r > 0 should be(true)
    }

    "request cmc tickers in USD and persist (CMCCrawlerActor)" in {
      tokens.foreach { t =>
        t.meta.usdPrice should be(1000)
      }
      val f = for {
        cmcResponse <- getMockedCMCTickers()
        rateResponse <- currencyManager.getUsdCnyCurrency()
        tickersToPersist <- if (cmcResponse.data.nonEmpty && rateResponse > 0) {
          for {
            t <- persistTickers(rateResponse, cmcResponse.data)
            tokens <- dbModule.tokenMetadataDal.getTokens()
            _ <- updateTokenPrice(cmcResponse.data, tokens)
          } yield t
        } else {
          Future.successful(Seq.empty)
        }
        // verify result
        tickers_ <- dbModule.CMCTickersInUsdDal.countTickersByRequestTime(
          tickersToPersist.head.requestTime
        )
        tokens_ <- dbModule.tokenMetadataDal.getTokens()
      } yield (cmcResponse, tickersToPersist, tickers_, tokens_)
      val q1 = Await.result(
        f.mapTo[
          (
              TickerDataInfo,
              Seq[CMCTickersInUsd],
              Int,
              Seq[TokenMetadata]
          )
        ],
        50.second
      )
      q1._1.data.length should be(2072)
      q1._2.length should be(2073) // CNY added
      tickers = q1._2
      q1._3 should be(2073)
      q1._4.exists(_.usdPrice != 1000) should be(true)
    }
    "convert USD tickers to all quote markets (ExternalDataRefresher)" in {
      val (allTickersInUSD, allTickersInCNY, effectiveTickers) =
        refreshTickers()
      val tickerMap = marketQuoteTokens.map { market =>
        (market, effectiveTickers.filter(_.market == market))
      }.toMap

      // get a random market
      val randomMarket = marketQuoteTokens.toList(
        (new util.Random).nextInt(marketQuoteTokens.size)
      )
      tickerMap(randomMarket).nonEmpty should be(true)

      // get a random position
      val p = (new util.Random).nextInt(allTickersInUSD.size)
      val tickerInUsd = allTickersInUSD(p)
      val tickerInCny = allTickersInCNY(p)

      // verify ticker in USD and CNY
      val cnyTousd =
        tickers.find(t => t.symbol == "CNY" && t.slug == "rmb")
      tickerInUsd.symbol should equal(tickerInCny.symbol)
      val cnyTickerVerify =
        tickerManager.convertUsdTickersToCny(
          Seq(tickerInUsd),
          cnyTousd
        )
      tickerInUsd.price should not equal tickerInCny.price
      cnyTickerVerify.nonEmpty should be(true)
      cnyTickerVerify.head should equal(tickerInCny)
    }
  }

  private def getMockedCMCTickers() = {
    import scala.io.Source
    val fileContents = Source.fromResource("cmc.data").getLines.mkString

    val res = parser.fromJsonString[TickerDataInfo](fileContents)
    res.status match {
      case Some(r) if r.errorCode == 0 =>
        Future.successful(res.copy(data = res.data))
      case Some(r) if r.errorCode != 0 =>
        log.error(
          s"Failed request CMC, code:[${r.errorCode}] msg:[${r.errorMessage}]"
        )
        Future.successful(res)
      case m =>
        log.error(s"Failed request CMC, return:[$m]")
        Future.successful(TickerDataInfo(Some(TickerStatus(errorCode = 404))))
    }
  }

  private def updateTokenPrice(
      usdTickers: Seq[CMCTickerData],
      tokens: Seq[TokenMetadata]
    ) = {
    var changedTokens = Seq.empty[TokenMetadata]
    tokens.foreach { token =>
      val priceQuote =
        usdTickers.find(_.slug == token.slug).flatMap(_.quote.get("USD"))
      val usdPriceQuote = priceQuote.getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"can not found slug:[${token.slug}] price in USD"
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
      usdTocnyRate: Double,
      tickers_ : Seq[CMCTickerData]
    ) =
    for {
      _ <- Future.unit
      tickersToPersist = tickerManager.convertCMCResponseToPersistence(
        tickers_
      )
      cnyTicker = CMCTickersInUsd(
        0,
        "RMB",
        "CNY",
        "rmb",
        0,
        0,
        0,
        "2019-01-01T00:00:00.000Z",
        0,
        0,
        "2019-01-01T00:00:00.000Z",
        Some(
          CMCTickersInUsd.Quote(
            price = tickerManager
              .toDouble(BigDecimal(1) / BigDecimal(usdTocnyRate)),
            lastUpdated = "2019-01-01T00:00:00.000Z"
          )
        )
      )
      now = timeProvider.getTimeSeconds()
      _ = tickers =
        tickersToPersist.+:(cnyTicker).map(t => t.copy(requestTime = now))
      fixGroup = tickers.grouped(20).toList
      _ <- Future.sequence(
        fixGroup.map(dbModule.CMCTickersInUsdDal.saveTickers)
      )
      updateSucc <- dbModule.CMCTickersInUsdDal.updateEffective(now)
    } yield {
      if (updateSucc != ErrorCode.ERR_NONE) {
        log.error(s"CMC persist failed, code:$updateSucc")
        Seq.empty
      } else {
        tickers
      }
    }

  private def refreshTickers() = {
    assert(tickers.nonEmpty)
    val allTickersInUSD = tickers.map(tickerManager.convertPersistToExternal)
    val cnyToUsd =
      tickers.find(t => t.symbol == "CNY" && t.slug == "rmb")
    assert(cnyToUsd.nonEmpty)
    assert(cnyToUsd.get.usdQuote.nonEmpty)
    val allTickersInCNY = tickers.map { t =>
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
    val effectiveTickers = tickerManager
      .convertPersistenceToAllQuoteMarkets(
        tickers,
        marketQuoteTokens
      )
      .filter(isEffectiveMarket)
    (allTickersInUSD, allTickersInCNY, effectiveTickers)
  }

  private def isEffectiveMarket(ticker: ExternalTickerInfo): Boolean = {
    effectiveMarketSymbols.contains((ticker.symbol, ticker.market))
  }

}