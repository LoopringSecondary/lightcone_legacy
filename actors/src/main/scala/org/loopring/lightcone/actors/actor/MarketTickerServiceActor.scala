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

package org.loopring.lightcone.actors.actor

import akka.actor.{ Actor, ActorSystem }
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.scaladsl.SlickSession
import org.loopring.lightcone.actors.marketcap.DatabaseAccesser
import org.loopring.lightcone.proto.market_cap._
import scala.concurrent.Future
import akka.pattern.pipe
import org.loopring.lightcone.actors.base
import org.loopring.lightcone.actors.marketcap.{ CacherSettings, ProtoBufMessageCacher }
import org.loopring.lightcone.proto.deployment.MarketTickerServiceSettings

object MarketTickerServiceActor
  extends base.Deployable[MarketTickerServiceSettings] {
  val name = "market_ticker_service_actor"

  def getCommon(s: MarketTickerServiceSettings) =
    base.CommonSettings(None, s.roles, s.instances)
}

class MarketTickerServiceActor(
  implicit
  system: ActorSystem,
  mat: ActorMaterializer,
  session: SlickSession) extends DatabaseAccesser with Actor {

  import session.profile.api._
  import system.dispatcher

  implicit val settings = CacherSettings(system.settings.config)

  implicit val saveExchangeTickerInfo = (info: ExchangeTickerInfo) ⇒
    sqlu"""INSERT INTO t_exchange_ticker_info(symbol, market,
          exchange, price, price_usd, price_cny, volume_24h_usd, volume_24h,volume_24h_from,percent_change_utc0,alias,last_updated) VALUES(
          ${info.symbol}, ${info.market}, ${info.exchange}, ${info.price},
          ${info.priceUsd}, ${info.priceCny}, ${info.volume24HUsd}, ${info.volume24H},${info.volume24HFrom},${info.percentChangeUtc0},${info.alias},${info.lastUpdated}) ON DUPLICATE KEY UPDATE price=${info.price},
          price_usd=${info.priceUsd},price_cny=${info.priceCny},volume_24h_usd=${info.volume24HUsd},volume_24h=${info.volume24H},
          volume_24h_from=${info.volume24HFrom},percent_change_utc0=${info.percentChangeUtc0},alias=${info.alias},last_updated=${info.lastUpdated}"""

  implicit val toGetExchangeTickerInfo = (r: ResultRow) ⇒
    ExchangeTickerInfo(symbol = r <<, market = r <<, exchange = r <<,
      price = r <<, priceUsd = r <<, priceCny = r <<, volume24HUsd = r <<,
      volume24HFrom = r <<, volume24H = r <<, percentChangeUtc0 = r <<, alias = r <<, lastUpdated = r <<)

  val cacherExchangeTickerInfo = new ProtoBufMessageCacher[GetExchangeTickerInfoRes]
  val exchangeTickerInfoKey = "EXCHANGE_TICKER_INFO_"

  override def receive: Receive = {
    case info: ExchangeTickerInfo ⇒

      saveOrUpdate(info)

    case req: GetExchangeTickerInfoReq ⇒
      //优先查询缓存，缓存没有再查询数据表并存入缓存
      val res = cacherExchangeTickerInfo.getOrElse(buildCacheKey(req.symbol, req.market), Some(600)) {
        val resp: Future[GetExchangeTickerInfoRes] =
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
             where symbol = ${req.symbol}
             and market = ${req.market}
          """
            .list[ExchangeTickerInfo].map(GetExchangeTickerInfoRes(_))
        resp.map(Some(_))
      }

      res.map {
        case Some(r) ⇒ r
        case _ ⇒ throw new Exception("data in table is null. Please find the reason!")
      } pipeTo sender

  }

  def buildCacheKey(symbol: String, market: String) = {
    s"$exchangeTickerInfoKey${symbol.toUpperCase()}_${market.toUpperCase()}"
  }

}
