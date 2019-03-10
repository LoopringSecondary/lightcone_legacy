/*

  Copyright 2017 Loopring Project Ltd (Loopring Foundation).

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

*/

package io.lightcone.relayer.integration
import io.lightcone.core._
import io.lightcone.persistence._
import io.lightcone.relayer.implicits._
import io.lightcone.relayer.external.CMCResponse.CMCTickerData
import io.lightcone.relayer.external._
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import org.slf4s.Logging
import scalapb.json4s.Parser

import scala.concurrent._
import scala.concurrent.duration._

//数据库的prepare
trait DbHelper extends WordSpecLike with BeforeAndAfterAll with Logging {

  override protected def beforeAll(): Unit = {
    prepareDbModule(dbModule)
    prepareMetadata(dbModule, metadataManager, fiatExchangeRateFetcher)
    super.beforeAll()
  }

  def prepareDbModule(dbModule: DatabaseModule) = {

    dbModule.tables.map { t =>
      t.deleteByFilter(_ => true)
    }
//    dbModule.createTables()

    dbModule.tokenMetadataDal.saveTokenMetadatas(TOKENS)
    dbModule.tokenInfoDal.saveTokenInfos(TOKENS.map { t =>
      TokenInfo(t.symbol)
    })
    dbModule.cmcCrawlerConfigForTokenDal.saveConfigs(TOKEN_SLUGS_SYMBOLS.map {
      t =>
        CMCCrawlerConfigForToken(t._1, t._2)
    })
    dbModule.marketMetadataDal.saveMarkets(MARKETS)

  }

  //TODO:太复杂了，不需要这么多代码，应当很简化的
  def prepareMetadata(
      dbModule: DatabaseModule,
      metadataManager: MetadataManager,
      fiatExchangeRateFetcher: FiatExchangeRateFetcher
    ) = {

    var tickers: Seq[TokenTickerRecord] = Seq.empty[TokenTickerRecord]

    val tokens = TOKENS.map { t =>
      Token(Some(t), Some(TokenInfo(symbol = t.symbol)), 0.1)
    }

    val markets = MARKETS.map { m =>
      Market(
        Some(m),
        Some(
          MarketTicker(
            baseTokenSymbol = m.baseTokenSymbol,
            quoteTokenSymbol = m.quoteTokenSymbol,
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
      tokenTickers <- getMockedCMCTickers().recover {
        case e: Exception =>
          Seq.empty
      }
      tokenSymbolSlugs_ <- dbModule.cmcCrawlerConfigForTokenDal.getConfigs()
      tokenTickers_ = filterSlugTickers(tokenSymbolSlugs_, tokenTickers)
      currencyTickers <- fiatExchangeRateFetcher
        .fetchExchangeRates(
          CURRENCY_EXCHANGE_PAIR
        )
        .recover {
          case e: Exception =>
            Seq.empty
        }
      persistTickers <- if (tokenTickers_.nonEmpty && currencyTickers.nonEmpty) {
        persistTickers(
          currencyTickers,
          tokenTickers_,
          dbModule
        )
      } else {
        if (tokenTickers_.nonEmpty) log.error("failed request CMC tickers")
        if (currencyTickers.nonEmpty)
          log.error("failed request Sina currency rate")
        Future.successful(Seq.empty)
      }
    } yield { log.info(s"External Tickers initialize done...") }
    Await.result(initialize.mapTo[Unit], 50.second)
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
    val parser = new Parser(preservingProtoFieldNames = true) //protobuf 序列化为json不使用驼峰命名

    val fileContents = Source.fromResource("cmc.data").getLines.mkString
    val res = parser.fromJsonString[CMCResponse](fileContents)
    res.status match {
      case Some(r) if r.errorCode == 0 =>
        Future {
          fillTickersToPersistence(res.data)
        }
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
    val a = fillERC20TokenTickersToPersistence(tickersInUsd)
    val b = fillQuoteTickersToPersistence(tickersInUsd)
    a ++ b
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
          p.tokenAddress.toLowerCase,
          t.symbol.toLowerCase(),
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
      TokenTickerRecord(
        currency.getAddress().toLowerCase(),
        t.toUpperCase,
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
    }
  }

  private def persistTickers(
      currencyTickersInUsd: Seq[TokenTickerRecord],
      tokenTickersInUsd: Seq[TokenTickerRecord],
      dbModule: DatabaseModule
    )(
      implicit
      ec: ExecutionContext
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
