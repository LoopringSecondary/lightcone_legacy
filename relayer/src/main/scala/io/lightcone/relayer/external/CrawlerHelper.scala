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
import org.slf4s.Logging
import io.lightcone.core.{ErrorCode, ErrorException}
import io.lightcone.persistence.{CMCCrawlerConfigForToken, TokenTickerRecord}
import io.lightcone.relayer.external.CMCResponse.CMCTickerData

object CrawlerHelper extends Logging {

  val utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS Z")

  def fillTokenTickersToPersistence(
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

  def fillCurrencyTickersToPersistence(exchangeRate: Map[String, Double]) = {
    exchangeRate.map { k =>
      val price = scala.util
        .Try(
          (BigDecimal(1) / BigDecimal(k._2))
            .setScale(8, BigDecimal.RoundingMode.HALF_UP)
            .toDouble
        )
        .toOption
        .getOrElse(0.0)
      val currencies = k._1.split("-")
      new TokenTickerRecord(
        symbol = currencies(1),
        price = price,
        dataSource = "Sina"
      )
    }.toSeq
  }

  def getQuote(ticker: CMCTickerData) = {
    if (ticker.quote.get("USD").isEmpty) {
      log.error(s"CMC not return ${ticker.symbol} quote for USD")
      throw ErrorException(
        ErrorCode.ERR_INTERNAL_UNKNOWN,
        s"CMC not return ${ticker.symbol} quote for USD"
      )
    }
    ticker.quote("USD")
  }

  def normalize(record: TokenTickerRecord) = {
    record.copy(
      tokenAddress = record.tokenAddress.toLowerCase,
      symbol = record.symbol.toUpperCase
    )
  }

  def filterSlugTickers(
      tokenSymbolSlugs: Seq[CMCCrawlerConfigForToken],
      tickers: Seq[CMCTickerData]
    ) = {
    val slugMap = tokenSymbolSlugs.map(t => t.slug -> t.symbol).toMap
    val slugs = slugMap.keySet
    tickers.filter(t => slugs.contains(t.slug)).map { t =>
      t.copy(symbol = slugMap(t.slug))
    }
  }

  def normalizeTicker(ticker: CMCTickerData): CMCTickerData =
    ticker.copy(
      symbol = ticker.symbol.toUpperCase(),
      slug = ticker.slug.toLowerCase()
    )

  def convertDateToSecond(utcDateStr: String) = {
    utcFormat
      .parse(utcDateStr.replace("Z", " UTC"))
      .getTime / 1000
  }

  def toDouble(bigDecimal: BigDecimal): Double =
    scala.util
      .Try(bigDecimal.setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble)
      .toOption
      .getOrElse(0)
}
