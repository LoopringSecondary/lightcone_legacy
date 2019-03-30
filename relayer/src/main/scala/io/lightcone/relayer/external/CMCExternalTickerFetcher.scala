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
import io.lightcone.relayer.actors.ExternalCrawlerActor
import io.lightcone.relayer.external.CMCResponse._

class CMCExternalTickerFetcher @Inject()(
    implicit
    val config: Config,
    val system: ActorSystem,
    val ec: ExecutionContext,
    val dbModule: DatabaseModule,
    val materializer: ActorMaterializer)
    extends ExternalTickerFetcher
    with Logging {

  val cmcConfig = config.getConfig(ExternalCrawlerActor.name)
  val requestHeader = cmcConfig.getString("cmc.header")
  val apiKey = cmcConfig.getString("cmc.api-key")
  val prefixUrl = cmcConfig.getString("cmc.prefix-url")
  val limitSize = cmcConfig.getString("cmc.limit-size")
  val convertCurrency = cmcConfig.getString("cmc.convert-currency")

  val utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS Z")

  val uri =
    s"$prefixUrl/v1/cryptocurrency/listings/latest?start=1&limit=${limitSize}&convert=${convertCurrency}"
  val rawHeader = RawHeader(requestHeader, apiKey)

  val parser = new Parser(preservingProtoFieldNames = true) //protobuf 序列化为json不使用驼峰命名

  def fetchExternalTickers() = {
    for {
      symbolSlugs <- dbModule.cmcCrawlerConfigForTokenDal.getConfigs()
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
            .map(parser.fromJsonString[CMCResponse])
            .map { j =>
              j.status match {
                case Some(r) if r.errorCode == 0 =>
                  filterSupportTickers(symbolSlugs, j.data)
                case Some(r) if r.errorCode != 0 =>
                  log.error(
                    s"Failed request CMC, code:[${r.errorCode}] msg:[${r.errorMessage}]"
                  )
                  Seq.empty
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

  def filterSupportTickers(
      tokenSymbolSlugs: Seq[CMCCrawlerConfigForToken],
      tickers: Seq[CMCTickerData]
    ): Seq[TokenTickerRecord] = {
    fillTokenTickersToPersistence(
      filterSlugTickers(tokenSymbolSlugs, tickers)
        .map(normalizeTicker)
    )
  }

  private def fillTokenTickersToPersistence(
      tickersInUsd: Seq[CMCTickerData]
    ): Seq[TokenTickerRecord] = {
    tickersInUsd.map { t =>
      val q = getQuote(t)
      val tokenAddress = t.platform match {
        case None    => ""
        case Some(p) => p.tokenAddress
      }
      normalize(
        TokenTickerRecord(
          tokenAddress,
          t.symbol,
          q.price,
          q.volume24H,
          q.percentChange1H,
          q.percentChange24H,
          q.percentChange7D,
          q.marketCap,
          0,
          false,
          TokenTickerRecord.Type.TOKEN,
          "CMC"
        )
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

  private def normalize(record: TokenTickerRecord) = {
    record.copy(
      tokenAddress = record.tokenAddress.toLowerCase,
      symbol = record.symbol.toUpperCase
    )
  }

  private def filterSlugTickers(
      tokenSymbolSlugs: Seq[CMCCrawlerConfigForToken],
      tickers: Seq[CMCTickerData]
    ) = {
    val slugMap = tokenSymbolSlugs.map(t => t.slug -> t.symbol).toMap
    val slugs = slugMap.keySet
    tickers.filter(t => slugs.contains(t.slug)).map { t =>
      t.copy(symbol = slugMap(t.slug))
    }
  }

  private def normalizeTicker(ticker: CMCTickerData): CMCTickerData =
    ticker.copy(
      symbol = ticker.symbol.toUpperCase(),
      slug = ticker.slug.toLowerCase()
    )

  private def convertDateToSecond(utcDateStr: String) = {
    utcFormat
      .parse(utcDateStr.replace("Z", " UTC"))
      .getTime / 1000
  }

  private def toDouble(bigDecimal: BigDecimal): Double =
    scala.util
      .Try(bigDecimal.setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble)
      .toOption
      .getOrElse(0)
}
