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
import org.loopring.lightcone.proto.auxiliary._

import scala.concurrent.Future
import akka.pattern.pipe
import com.google.inject.Inject
import org.loopring.lightcone.actors.base
import org.loopring.lightcone.actors.marketcap.{ CacherSettings, ProtoBufMessageCacher }
import org.loopring.lightcone.proto.deployment.MarketTickerServiceSettings
import org.loopring.lightcone.auxiliary.service._

object MarketTickerServiceActor
  extends base.Deployable[MarketTickerServiceSettings] {
  val name = "market_ticker_service_actor"

  def getCommon(s: MarketTickerServiceSettings) =
    base.CommonSettings(None, s.roles, s.instances)
}

class MarketTickerServiceActor @Inject() (service: ExchangeTickerService)(
    implicit
    system: ActorSystem,
    mat: ActorMaterializer,
    session: SlickSession
) extends Actor {

  import system.dispatcher
  implicit val settings = CacherSettings(system.settings.config)

  val cacherExchangeTickerInfo = new ProtoBufMessageCacher[GetExchangeTickerInfoRes]
  val exchangeTickerInfoKey = "EXCHANGE_TICKER_INFO_"

  override def receive: Receive = {
    case info: ExchangeTickerInfo ⇒

      service.saveOrUpdate(info)

    case req: GetExchangeTickerInfoReq ⇒
      //入参
      val symbol = req.marketPair.split("-")(0)
      val market = req.marketPair.split("-")(1)

      //优先查询缓存，缓存没有再查询数据表并存入缓存
      val res = cacherExchangeTickerInfo.getOrElse(buildCacheKey(symbol, market), Some(600)) {
        val resp: Future[GetExchangeTickerInfoRes] = service.queryExchangeTicker(symbol, market)
        resp.map(Some(_))
      }

      res.map {
        case Some(r) ⇒ r
        case _       ⇒ throw new Exception("data in table is null. Please find the reason!")
      } pipeTo sender

  }

  def buildCacheKey(symbol: String, market: String) = {
    s"$exchangeTickerInfoKey${symbol.toUpperCase()}_${market.toUpperCase()}"
  }

}
