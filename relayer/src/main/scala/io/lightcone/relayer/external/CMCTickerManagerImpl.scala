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

package io.lightcone.relayer.external

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.slf4s.Logging
import scalapb.json4s.Parser
import io.lightcone.core._
import scala.concurrent.ExecutionContext
import com.google.inject._
import io.lightcone.core.ErrorException
import io.lightcone.persistence.{CMCTokenSlug, ThirdPartyTokenPrice}
import io.lightcone.persistence.ThirdPartyTokenPrice.Quote
import io.lightcone.relayer.data._
import io.lightcone.relayer.actors.CMCCrawlerActor

class CMCTickerManagerImpl @Inject()(
    implicit
    val config: Config,
    val system: ActorSystem,
    val ec: ExecutionContext,
    val materializer: ActorMaterializer)
    extends TickerManager
    with Logging {

  val cmcConfig = config.getConfig(CMCCrawlerActor.name)
  val requestHeader = cmcConfig.getString("cmc.header")
  val apiKey = cmcConfig.getString("cmc.api-key")
  val prefixUrl = cmcConfig.getString("cmc.prefix-url")
  val limitSize = cmcConfig.getString("cmc.limit-size")
  val convertCurrency = cmcConfig.getString("cmc.convert-currency")

  val uri =
    s"$prefixUrl/v1/cryptocurrency/listings/latest?start=1&limit=${limitSize}&convert=${convertCurrency}"
  val rawHeader = RawHeader(requestHeader, apiKey)

  val parser = new Parser(preservingProtoFieldNames = true) //protobuf 序列化为json不使用驼峰命名

  def requestCMCTickers() = {
    for {
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.GET,
          uri = Uri(uri)
        ).withHeaders(rawHeader)
      )
      res <- response match {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          entity.dataBytes
            .map(_.utf8String)
            .runReduce(_ + _)
            .map(parser.fromJsonString[TickerDataInfo])
            .map { j =>
              j.status match {
                case Some(r) if r.errorCode == 0 =>
                  j.copy(data = j.data.map(normalizeTicker))
                case Some(r) if r.errorCode != 0 =>
                  log.error(
                    s"Failed request CMC, code:[${r.errorCode}] msg:[${r.errorMessage}]"
                  )
                  j
                case m =>
                  log.error(s"Failed request CMC, return:[$m]")
                  throw ErrorException(
                    ErrorCode.ERR_INTERNAL_UNKNOWN,
                    "Failed request CMC"
                  )
              }
            }

        case m =>
          log.error(s"get ticker data from coinmarketcap failed:$m")
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            "Failed request CMC"
          )
      }
    } yield res
  }

  def convertCMCResponseToPersistence(
      tickers_ : Seq[CMCTickerData]
    ): Seq[ThirdPartyTokenPrice] = {
    tickers_.map { t =>
      if (t.quote.get("USD").isEmpty) {
        log.error(s"CMC not return ${t.symbol} quote for USD")
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"CMC not return ${t.symbol} quote for USD"
        )
      }
      val q = t.quote("USD")
      val usdQuote = ThirdPartyTokenPrice.Quote(
        q.price,
        q.volume24H,
        q.percentChange1H,
        q.percentChange24H,
        q.percentChange7D,
        q.marketCap
      )
      ThirdPartyTokenPrice(
        t.slug,
        Some(usdQuote)
      )
    }
  }

  def convertPersistenceToAllQuoteMarkets(
      usdTickers: Seq[ThirdPartyTokenPrice],
      slugSymbols: Seq[CMCTokenSlug],
      marketQuoteTokens: Set[String]
    ): Seq[ExternalTickerInfo] = {
    val tickersInUsdWithQuoteMarkets =
      getTickersWithAllQuoteMarkets(usdTickers, slugSymbols, marketQuoteTokens)
    val tickersWithAllQuoteMarkets = convertToAllQuoteMarketsInUsd(
      tickersInUsdWithQuoteMarkets
    )
    tickersWithAllQuoteMarkets
      .filter(c => c.symbol != c.market) // LRC-LRC
      .filterNot(c => c.symbol == "ETH" && c.market == "WETH") // ETH-WETH
  }

  private def getTickersWithAllQuoteMarkets(
      usdTickers: Seq[ThirdPartyTokenPrice],
      slugSymbols: Seq[CMCTokenSlug],
      supportMarketSymbols: Set[String]
    ) = {
    val marketsQuote =
      getAllMarketQuoteInUSD(usdTickers, slugSymbols, supportMarketSymbols)
    usdTickers.map { usdTicker =>
      val usdQuote = usdTicker.usdQuote
      if (usdQuote.isEmpty) {
        log.error(s"can not found slug:${usdTicker.slug} quote for USD")
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"can not found ${usdTicker.slug} quote for USD"
        )
      }
      val ticker = convertPersistenceWithUsdQuote(usdTicker, slugSymbols)
      val priceQuote = usdQuote.get
      val quoteMap = marketsQuote.foldLeft(ticker.quote) { (map, marketQuote) =>
        //添加市场代币的Quote ("LRC", "WETH", "TUSD", "USDT")
        map + (marketQuote._1 -> convertQuote(priceQuote, marketQuote._2))
      }
      //更新token的quote属性
      ticker.copy(quote = quoteMap)
    }
  }

  private def convertPersistenceWithUsdQuote(
      t: ThirdPartyTokenPrice,
      slugSymbols: Seq[CMCTokenSlug]
    ) = {
    val q = t.usdQuote.get
    val slugSymbol = slugSymbols
      .find(_.slug == t.slug)
      .getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found slug: ${t.slug} symbol"
        )
      )
    new CMCTickerData(
      symbol = slugSymbol.symbol,
      slug = t.slug,
      quote = Map(
        "USD" -> Quote(
          q.price,
          q.volume24H,
          q.percentChange1H,
          q.percentChange24H,
          q.percentChange7D,
          q.marketCap
        )
      )
    )
  }

  private def convertToAllQuoteMarketsInUsd(
      tickersInUsd: Seq[CMCTickerData]
    ): Seq[ExternalTickerInfo] = {
    tickersInUsd.flatMap { ticker =>
      val id = ticker.id
      val name = ticker.name
      val symbol = ticker.symbol
      val websiteSlug = ticker.slug
      val rank = ticker.cmcRank
      val circulatingSupply = ticker.circulatingSupply
      val totalSupply = ticker.totalSupply
      val maxSupply = ticker.maxSupply

      ticker.quote.map { priceQuote =>
        val market = priceQuote._1
        val quote = priceQuote._2
        val price = quote.price
        val volume24h = quote.volume24H
        val marketCap = quote.marketCap
        val percentChange1h = quote.percentChange1H
        val percentChange24h = quote.percentChange24H
        val percentChange7d = quote.percentChange7D
        val pair = symbol + "-" + market

        ExternalTickerInfo(
          symbol,
          ticker.slug,
          market,
          pair,
          price,
          volume24h,
          percentChange1h,
          percentChange24h,
          percentChange7d
        )
      }
    }
  }

  // 找到市场代币对USD的priceQuote
  private def getAllMarketQuoteInUSD(
      tickers: Seq[ThirdPartyTokenPrice],
      slugSymbols: Seq[CMCTokenSlug],
      supportMarketSymbols: Set[String]
    ): Seq[(String, ThirdPartyTokenPrice.Quote)] = {
    supportMarketSymbols.toSeq.map { s =>
      val symbol = if (s == "WETH") "ETH" else s
      val slugSymbol = slugSymbols
        .find(_.symbol == symbol)
        .getOrElse(
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"not found symbol: ${symbol}"
          )
        )
      val priceQuote =
        tickers.find(_.slug == slugSymbol.slug).flatMap(_.usdQuote)
      if (priceQuote.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"can not found ${s} price in USD"
        )
      (
        s,
        priceQuote.get
      )
    }
  }

  //锚定市场币的priceQuote换算
  private def convertQuote(
      tokenQuote: Quote,
      marketQuote: Quote
    ): Quote = {
    val price = toDouble(BigDecimal(tokenQuote.price / marketQuote.price))
    val volume_24h = toDouble(
      BigDecimal(tokenQuote.volume24H / tokenQuote.price) * price
    )
    val market_cap = toDouble(
      BigDecimal(tokenQuote.marketCap / tokenQuote.price) * price
    )
    val percent_change_1h = BigDecimal(1 + tokenQuote.percentChange1H) / BigDecimal(
      1 + marketQuote.percentChange1H
    ) - 1
    val percent_change_24h = BigDecimal(1 + tokenQuote.percentChange24H) / BigDecimal(
      1 + marketQuote.percentChange24H
    ) - 1
    val percent_change_7d = BigDecimal(1 + tokenQuote.percentChange7D) / BigDecimal(
      1 + marketQuote.percentChange7D
    ) - 1
    Quote(
      price,
      volume_24h,
      toDouble(percent_change_1h),
      toDouble(percent_change_24h),
      toDouble(percent_change_7d),
      toDouble(market_cap)
    )
  }

}
