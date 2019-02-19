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

import io.lightcone.cmc._
import io.lightcone.core._
import io.lightcone.persistence._
import io.lightcone.persistence.RequestJob.JobType
import io.lightcone.relayer.actors._
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

  val USD_CNY = 6.873
  private val tokens = metadataManager.getTokens
  private val marketQuoteTokens = metadataManager.getMarketQuoteTokens()
  private val effectiveMarketSymbols = metadataManager
    .getMarkets()
    .filter(_.status != MarketMetadata.Status.TERMINATED)
    .map(m => (m.baseTokenSymbol, m.quoteTokenSymbol))

  "cmc crawler" must {
    "request cmc tickers in USD and persist (CMCCrawlerActor)" in {
      tokens.foreach { t =>
        t.meta.usdPrice should be(1000)
      }
      val f = for {
        savedJob <- dbModule.requestJobDal.saveJob(
          RequestJob(
            jobType = JobType.TICKERS_FROM_CMC,
            requestTime = timeProvider.getTimeSeconds()
          )
        )
        cmcResponse <- getMockedCMCTickers()
        tickersToPersist <- if (cmcResponse.data.nonEmpty) {
          val status = cmcResponse.status.getOrElse(TickerStatus())
          for {
            _ <- dbModule.requestJobDal.updateStatusCode(
              savedJob.batchId,
              status.errorCode,
              timeProvider.getTimeSeconds()
            )
            tickers_ <- persistTickers(savedJob.batchId, cmcResponse.data)
            _ <- dbModule.requestJobDal.updateSuccessfullyPersisted(
              savedJob.batchId
            )
            tokens <- dbModule.tokenMetadataDal.getTokens()
            _ <- updateTokenPrice(cmcResponse.data, tokens)
          } yield tickers_
        } else {
          Future.successful(Seq.empty)
        }
        // verify result
        tickers_ <- dbModule.CMCTickersInUsdDal.countTickersByJob(
          savedJob.batchId
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
      q1._2.length should be(2072)
      tickers = q1._2
      q1._3 should be(2072)
      q1._4.exists(_.usdPrice != 1000) should be(true)
    }
    "convert USD tickers to all quote markets (ExternalDataRefresher)" in {
      val (tickersInUSD, tickersInCNY) = refreshTickers()
      val tickerMapInUSD = marketQuoteTokens.map { market =>
        (market, tickersInUSD.filter(_.market == market))
      }.toMap

      val tickerMapInCNY = marketQuoteTokens.map { market =>
        (market, tickersInCNY.filter(_.market == market))
      }.toMap

      // get a random market
      val randomMarket = marketQuoteTokens.toList(
        (new util.Random).nextInt(marketQuoteTokens.size)
      )
      // get a random position
      val marketTickersInUsd = tickerMapInUSD(randomMarket)
      val p = (new util.Random).nextInt(marketTickersInUsd.size)
      val usdTicker = marketTickersInUsd(p)
      val cnyTicker = tickerMapInCNY(randomMarket)(p)
      // verify ticker in USD and CNY
      usdTicker.symbol should equal(cnyTicker.symbol)
      val cnyTickerVerify =
        tickerManager.convertUsdTickersToCny(
          Seq(usdTicker),
          Some(CurrencyRate(USD_CNY))
        )
      usdTicker.price should not equal cnyTicker.price
      cnyTickerVerify.nonEmpty should be(true)
      cnyTickerVerify.head should equal(cnyTicker)
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
      batchId: Int,
      tickers_ : Seq[CMCTickerData]
    ) =
    for {
      _ <- Future.unit
      tickersToPersist = tickerManager.convertCMCResponseToPersistence(
        batchId,
        tickers_
      )
      fixGroup = tickersToPersist.grouped(20).toList
      _ <- Future.sequence(
        fixGroup.map(dbModule.CMCTickersInUsdDal.saveTickers)
      )
    } yield tickersToPersist

  private def refreshTickers() = {
    assert(tickers.nonEmpty)
    val tickersInUSD = tickerManager
      .convertPersistenceToAllQuoteMarkets(
        tickers,
        marketQuoteTokens
      )
      .filter(isEffectiveMarket)
    val tickersInCNY =
      tickerManager
        .convertUsdTickersToCny(
          tickersInUSD,
          Some(CurrencyRate(USD_CNY))
        )
        .filter(isEffectiveMarket)
    (tickersInUSD, tickersInCNY)
  }

  private def isEffectiveMarket(ticker: ExternalTickerInfo): Boolean = {
    effectiveMarketSymbols.contains((ticker.symbol, ticker.market))
  }

}
