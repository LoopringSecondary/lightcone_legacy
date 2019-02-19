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

import io.lightcone.cmc._
import io.lightcone.persistence.{CMCTickersInUsd, CurrencyRate}
import io.lightcone.relayer.rpc.ExternalTickerInfo
import scala.concurrent.{ExecutionContext, Future}

trait TickerManager {

  def requestCMCTickers(): Future[TickerDataInfo]

  def convertCMCResponseToPersistence(
      batchId: Int,
      tickers_ : Seq[CMCTickerData]
    ): Seq[CMCTickersInUsd]

  def convertPersistenceToAllSupportMarkets(
      usdTickers: Seq[CMCTickersInUsd],
      supportMarketSymbols: Set[String]
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

  def convertUsdTickersToCny(
      usdTickers: Seq[ExternalTickerInfo],
      usdToCny: Option[CurrencyRate]
    ) = {
    if (usdTickers.nonEmpty && usdToCny.nonEmpty && usdToCny.get.rate > 0) {
      usdTickers.map { t =>
        t.copy(price = toDouble(BigDecimal(t.price / usdToCny.get.rate)))
      }
    } else {
      Seq.empty
    }
  }
}
