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
import io.lightcone.relayer.data.cmc._
import io.lightcone.relayer.external._
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

  var tickers: Seq[ExternalTicker] = Seq.empty[ExternalTicker]

  var slugSymbols: Seq[CMCCrawlerConfigForToken] =
    Seq.empty[CMCCrawlerConfigForToken] // slug -> symbol

  private val tokens = metadataManager.getTokens.map(_.meta.symbol)
  private val effectiveMarketSymbols = metadataManager
    .getMarkets()
    .filter(_.status != MarketMetadata.Status.TERMINATED)
    .map(m => (m.baseTokenSymbol, m.quoteTokenSymbol))

  "cmc crawler" must {
    "sina currency rate" in {
      val r =
        Await.result(
          fiatExchangeRateFetcher
            .fetchExchangeRates(Seq(USD_RMB))
            .mapTo[Map[String, Double]],
          5.second
        )
      r.nonEmpty should be(true)
      r.contains(USD_RMB) should be(true)
    }

    "request cmc tickers in USD and persist (CMCCrawlerActor)" in {
      val f = for {
        cmcResponse <- getMockedCMCTickers()
        rateResponse <- fiatExchangeRateFetcher.fetchExchangeRates(Seq(USD_RMB))
        slugSymbols_ <- dbModule.cmcTickerConfigDal.getConfigs()
        tickersToPersist <- if (cmcResponse.nonEmpty && rateResponse.nonEmpty) {
          for {
            t <- persistTickers(
              rateResponse(USD_RMB),
              cmcResponse,
              slugSymbols_
            )
          } yield t
        } else {
          Future.successful(Seq.empty)
        }
        // verify result
        tickers_ <- dbModule.externalTickerDal.countTickers(
          tickersToPersist.head.timestamp
        )
        tokens_ <- dbModule.tokenMetadataDal.getTokens()
      } yield (cmcResponse, tickersToPersist, tickers_, tokens_, slugSymbols_)
      val q1 = Await.result(
        f.mapTo[
          (
              Seq[CMCTickerData],
              Seq[ExternalTicker],
              Int,
              Seq[TokenMetadata],
              Seq[CMCCrawlerConfigForToken]
          )
        ],
        50.second
      )
      q1._1.length should be(2072)
      q1._2.length should be(TOKEN_SLUGS_SYMBOLS.length + 1) // RMB added
      q1._3 should be(TOKEN_SLUGS_SYMBOLS.length + 1)
      q1._5.nonEmpty should be(true)
      tickers = q1._2
      slugSymbols = q1._5 ++ q1._1
        .map(t => CMCCrawlerConfigForToken(t.symbol, t.slug))
    }

    "convert USD tickers to all quote markets (ExternalDataRefresher)" in {
      val (allTickersInUSD, allTickersInCNY, effectiveTickers) =
        refreshTickers()

      // get a random position
      assert(allTickersInUSD.length == allTickersInCNY.length)
      val p = (new util.Random).nextInt(allTickersInUSD.size)
      val tickerInUsd = allTickersInUSD(p)
      val tickerInCny = allTickersInCNY(p)

      // verify ticker in USD and CNY
      val cnyToUsd =
        tickers.find(_.symbol == RMB)
      tickerInUsd.symbol should equal(tickerInCny.symbol)
      val cnyTickerVerify =
        CMCExternalTickerFetcher.convertUsdTickersToCny(
          Seq(tickerInUsd),
          cnyToUsd
        )
      tickerInUsd.price should not equal tickerInCny.price
      tickerInUsd.volume24H should not equal tickerInCny.volume24H
      tickerInUsd.volume24H < tickerInCny.volume24H should be(true)
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
        Future.successful(res.data)
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
      usdToCnyRate: Double,
      tickers_ : Seq[CMCTickerData],
      slugSymbols: Seq[CMCCrawlerConfigForToken]
    ) =
    for {
      _ <- Future.unit
      tickersToPersist = CMCExternalTickerFetcher
        .convertCMCResponseToPersistence(
          tickers_,
          slugSymbols
        )
      cnyTicker = ExternalTicker(
        RMB,
        CMCExternalTickerFetcher
          .toDouble(BigDecimal(1) / BigDecimal(usdToCnyRate))
      )
      now = timeProvider.getTimeSeconds()
      _ = tickers =
        tickersToPersist.+:(cnyTicker).map(t => t.copy(timestamp = now))
      fixGroup = tickers.grouped(20).toList
      _ <- Future.sequence(
        fixGroup.map(dbModule.externalTickerDal.saveTickers)
      )
      updateSucc <- dbModule.externalTickerDal.setValid(now)
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
    val tickers_ = tickers
      .filter(_.symbol != RMB)
      .filter(isEffectiveToken)
    val allTickersInUSD =
      tickers_
        .filter(isEffectiveToken)
        .map(CMCExternalTickerFetcher.convertPersistToExternal)
    val cnyToUsd =
      tickers.find(_.symbol == RMB)
    assert(cnyToUsd.nonEmpty)
    assert(cnyToUsd.get.priceUsd > 0)
    val allTickersInCNY = tickers_.filter(isEffectiveToken).map { t =>
      val t_ = CMCExternalTickerFetcher.convertPersistToExternal(t)
      t_.copy(
        price = CMCExternalTickerFetcher.toDouble(
          BigDecimal(t.priceUsd) / BigDecimal(
            cnyToUsd.get.priceUsd
          )
        ),
        volume24H = CMCExternalTickerFetcher.toDouble(
          BigDecimal(t.volume24H) / BigDecimal(
            cnyToUsd.get.priceUsd
          )
        )
      )
    }
    val effectiveMarketTickers = CMCExternalTickerFetcher.fillAllMarketTickers(
      tickers_,
      effectiveMarketSymbols
    )

    (allTickersInUSD, allTickersInCNY, effectiveMarketTickers)
  }

  private def isEffectiveToken(ticker: ExternalTicker): Boolean = {
    tokens.contains(ticker.symbol) && slugSymbols.exists(
      _.symbol == ticker.symbol
    )
  }

}
