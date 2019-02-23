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

import java.text.SimpleDateFormat
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
import io.lightcone.persistence._
import io.lightcone.relayer.actors.CMCCrawlerActor
import io.lightcone.relayer.data.cmc._

class CMCExternalTickerFetcher @Inject()(
    implicit
    val config: Config,
    val system: ActorSystem,
    val ec: ExecutionContext,
    val materializer: ActorMaterializer)
    extends ExternalTickerFetcher
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

  def fetchExternalTickers() = {
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
                  j.copy(
                    data = j.data.map(CMCExternalTickerFetcher.normalizeTicker)
                  )
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
}

object CMCExternalTickerFetcher extends Logging {

  val utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS Z")

  def convertCMCResponseToPersistence(
      tickers_ : Seq[CMCTickerData]
    ): Seq[ExternalTicker] = {
    tickers_.map { t =>
      if (t.quote.get("USD").isEmpty) {
        log.error(s"CMC not return ${t.symbol} quote for USD")
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"CMC not return ${t.symbol} quote for USD"
        )
      }
      val q = t.quote("USD")
      ExternalTicker(
        t.slug,
        q.price,
        q.volume24H,
        q.percentChange1H,
        q.percentChange24H,
        q.percentChange7D,
        q.marketCap
      )
    }
  }

  def fillAllMarketTickers(
      usdTickers: Seq[ExternalTicker],
      slugSymbols: Seq[CMCTickerConfig],
      effectiveMarketSymbols: Seq[(String, String)]
    ): Seq[ExternalMarketTickerInfo] = {
    effectiveMarketSymbols.map { market =>
      calculateMarketQuote(market._1, market._2, usdTickers, slugSymbols)
    }
  }

  private def getTickerBySlug(
      slug: String,
      usdTickers: Seq[ExternalTicker]
    ) = {
    usdTickers
      .find(t => t.slug == slug)
      .getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found ticker of slug: $slug"
        )
      )
  }

  private def calculateMarketQuote(
      baseTokenSymbol: String,
      quoteTokenSymbol: String,
      usdTickers: Seq[ExternalTicker],
      slugSymbols: Seq[CMCTickerConfig]
    ): ExternalMarketTickerInfo = {
    val baseSlug = getSlugBySymbol(baseTokenSymbol, slugSymbols)
    val quoteSlug = getSlugBySymbol(quoteTokenSymbol, slugSymbols)
    val baseTicker = getTickerBySlug(baseSlug, usdTickers)
    val quoteTicker = getTickerBySlug(quoteSlug, usdTickers)
    val price = toDouble(BigDecimal(baseTicker.priceUsd / quoteTicker.priceUsd))
    val volume_24h = toDouble(
      BigDecimal(baseTicker.volume24H / baseTicker.priceUsd) * price
    )
    val market_cap = toDouble(
      BigDecimal(baseTicker.marketCap / baseTicker.priceUsd) * price
    )
    val percent_change_1h = BigDecimal(1 + baseTicker.percentChange1H) / BigDecimal(
      1 + quoteTicker.percentChange1H
    ) - 1
    val percent_change_24h = BigDecimal(1 + baseTicker.percentChange24H) / BigDecimal(
      1 + quoteTicker.percentChange24H
    ) - 1
    val percent_change_7d = BigDecimal(1 + baseTicker.percentChange7D) / BigDecimal(
      1 + quoteTicker.percentChange7D
    ) - 1
    ExternalMarketTickerInfo(
      baseTokenSymbol,
      quoteTokenSymbol,
      s"$baseTokenSymbol-$quoteTokenSymbol",
      price,
      volume_24h,
      toDouble(percent_change_1h),
      toDouble(percent_change_24h),
      toDouble(percent_change_7d)
    )
  }

  def normalizeTicker(ticker: CMCTickerData): CMCTickerData =
    ticker.copy(
      symbol = ticker.symbol.toUpperCase(),
      slug = ticker.slug.toLowerCase()
    )

  def toDouble: PartialFunction[BigDecimal, Double] = {
    case s: BigDecimal =>
      scala.util
        .Try(s.setScale(8, BigDecimal.RoundingMode.HALF_UP).toDouble)
        .toOption
        .getOrElse(0)
  }

  def convertDateToSecond(utcDateStr: String) = {
    utcFormat
      .parse(utcDateStr.replace("Z", " UTC"))
      .getTime / 1000
  }

  def convertUsdTickersToCny(
      usdTickers: Seq[ExternalTokenTickerInfo],
      usdToCny: Option[ExternalTicker]
    ) = {
    if (usdTickers.nonEmpty && usdToCny.nonEmpty) {
      val cnyToUsd = usdToCny.get.priceUsd
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

  def convertPersistToExternal(
      ticker: ExternalTicker,
      slugSymbols: Seq[CMCTickerConfig]
    ) = {
    if (ticker.priceUsd <= 0) {
      throw ErrorException(
        ErrorCode.ERR_INTERNAL_UNKNOWN,
        s"invalid price:${ticker.priceUsd} with ticker slug ${ticker.slug}"
      )
    }
    val slugSymbol = slugSymbols
      .find(_.slug == ticker.slug)
      .getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found slug: ${ticker.slug} symbol"
        )
      )
    ExternalTokenTickerInfo(
      slugSymbol.symbol,
      ticker.priceUsd,
      ticker.volume24H,
      ticker.percentChange1H,
      ticker.percentChange24H,
      ticker.percentChange7D
    )
  }

  private def getSlugBySymbol(
      symbol: String,
      slugSymbols: Seq[CMCTickerConfig]
    ): String = {
    val slugSymbol = slugSymbols
      .find(_.symbol == symbol)
      .getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found symbol: ${symbol}"
        )
      )
    slugSymbol.slug
  }
}
