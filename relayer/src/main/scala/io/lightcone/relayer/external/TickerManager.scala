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
import io.lightcone.core.{ErrorCode, ErrorException}
import io.lightcone.external._
import io.lightcone.persistence.CMCTickersInUsd
import io.lightcone.relayer.rpc.ExternalTickerInfo
import org.slf4s.Logging
import scala.concurrent.Future

trait TickerManager extends Logging {

  val utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS Z")

  def requestCMCTickers(): Future[TickerDataInfo]

  def convertCMCResponseToPersistence(
      tickers_ : Seq[CMCTickerData]
    ): Seq[CMCTickersInUsd]

  def convertPersistenceToAllQuoteMarkets(
      usdTickers: Seq[CMCTickersInUsd],
      marketQuoteTokens: Set[String]
    ): Seq[ExternalTickerInfo]

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
      usdTickers: Seq[ExternalTickerInfo],
      usdToCny: Option[CMCTickersInUsd]
    ) = {
    if (usdTickers.nonEmpty && usdToCny.nonEmpty) {
      val cnyToUsd = usdToCny.get.usdQuote.get.price
      usdTickers.map { t =>
        t.copy(
          price = toDouble(BigDecimal(t.price) * BigDecimal(cnyToUsd))
        )
      }
    } else {
      Seq.empty
    }
  }

  def convertPersistToExternal(ticker: CMCTickersInUsd) = {
    if (ticker.usdQuote.isEmpty) {
      throw ErrorException(
        ErrorCode.ERR_INTERNAL_UNKNOWN,
        s"not found quote with ticker slug ${ticker.slug}"
      )
    }
    if (ticker.usdQuote.get.price <= 0) {
      throw ErrorException(
        ErrorCode.ERR_INTERNAL_UNKNOWN,
        s"invalid price:${ticker.usdQuote.get.price} with ticker slug ${ticker.slug}"
      )
    }
    val quote = ticker.usdQuote.get
    ExternalTickerInfo(
      ticker.name,
      ticker.symbol,
      ticker.slug,
      "",
      "",
      ticker.cmcRank,
      quote.price,
      quote.volume24H,
      quote.percentChange1H,
      quote.percentChange24H,
      quote.percentChange7D,
      convertDateToSecond(quote.lastUpdated)
    )
  }
}
