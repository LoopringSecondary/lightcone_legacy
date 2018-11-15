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

package org.loopring.lightcone.biz.marketcap

import com.google.inject.Inject
import org.loopring.lightcone.biz.model.ExchangeTicker
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.scaladsl.SlickSession
import scala.concurrent.Future

class ExchangeTickerServiceImpl @Inject() (
  implicit
  mat: ActorMaterializer,
  session: SlickSession) extends DatabaseAccesser with ExchangeTickerService {

  import session.profile.api._

  implicit val saveExchangeTicker = (info: ExchangeTicker) ⇒
    sqlu"""INSERT INTO t_exchange_ticker_info(symbol, market,
          exchange, price, price_usd, price_cny, volume_24h_usd, volume_24h,volume_24h_from,percent_change_utc0,alias,last_updated) VALUES(
          ${info.symbol}, ${info.market}, ${info.exchange}, ${info.price},
          ${info.priceUsd}, ${info.priceCny}, ${info.volume24HUsd}, ${info.volume24H},${info.volume24HFrom},${info.percentChangeUtc0},${info.alias},${info.lastUpdated}) ON DUPLICATE KEY UPDATE price=${info.price},
          price_usd=${info.priceUsd},price_cny=${info.priceCny},volume_24h_usd=${info.volume24HUsd},volume_24h=${info.volume24H},
          volume_24h_from=${info.volume24HFrom},percent_change_utc0=${info.percentChangeUtc0},alias=${info.alias},last_updated=${info.lastUpdated}"""

  implicit val toGetExchangeTicker = (r: ResultRow) ⇒
    ExchangeTicker(symbol = r <<, market = r <<, exchange = r <<,
      price = r <<, priceUsd = r <<, priceCny = r <<, volume24HUsd = r <<,
      volume24HFrom = r <<, volume24H = r <<, percentChangeUtc0 = r <<, alias = r <<, lastUpdated = r <<)

  override def insert(exchangeTicker: ExchangeTicker): Unit = {
    saveOrUpdate(exchangeTicker)
  }

  override def queryExchangeTicker(symbol: String, market: String): Future[Seq[ExchangeTicker]] = {
    sql"""select
              symbol,
              market,
              exchange,
              price,
              price_usd,
              price_cny,
              volume_24h_usd,
              volume_24h,
              volume_24h_from,
              percent_change_utc0,
              alias,
              last_updated
              from t_exchange_ticker_info
             where symbol = ${symbol}
             and market = ${market}
          """
      .list[ExchangeTicker]
  }

}

