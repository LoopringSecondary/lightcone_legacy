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
import org.loopring.lightcone.actors.marketcap.SeqTpro
import org.loopring.lightcone.actors.marketcap.DatabaseAccesser
import org.loopring.lightcone.proto.market_cap._
import org.loopring.lightcone.proto.deployment.TokenTickerServiceSettings
import scala.concurrent.Future
import akka.pattern.pipe
import org.loopring.lightcone.actors.base
import org.loopring.lightcone.actors.marketcap.{ CacherSettings, ProtoBufMessageCacher }

object TokenTickerServiceActor
  extends base.Deployable[TokenTickerServiceSettings] {
  val name = "token_ticker_service_actor"

  def getCommon(s: TokenTickerServiceSettings) =
    base.CommonSettings(None, s.roles, s.instances)
}

class TokenTickerServiceActor(implicit
  system: ActorSystem,
  mat: ActorMaterializer,
  session: SlickSession) extends DatabaseAccesser with Actor {

  import session.profile.api._
  import system.dispatcher

  implicit val settings = CacherSettings(system.settings.config)

  implicit val saveTokenTickerInfo = (info: TokenTickerInfo) ⇒
    sqlu"""INSERT INTO t_token_ticker_info(token_id, token_name,
          symbol, website_slug, market, cmc_rank, circulating_supply, total_supply,
              max_supply,price,volume_24h,market_cap,percent_change_1h,percent_change_24h,percent_change_7d,last_updated) VALUES(
          ${info.tokenId}, ${info.name}, ${info.symbol}, ${info.websiteSlug},
          ${info.market}, ${info.rank}, ${info.circulatingSupply}, ${info.totalSupply}, ${info.maxSupply},${info.price},${info.volume24H},
          ${info.marketCap},${info.percentChange1H},${info.percentChange24H},${info.percentChange7D},${info.lastUpdated}) ON DUPLICATE KEY UPDATE token_id=${info.tokenId},
          token_name = ${info.name},symbol=${info.symbol},cmc_rank=${info.rank},circulating_supply=${info.circulatingSupply},total_supply=${info.totalSupply},
          max_supply=${info.maxSupply},price=${info.price},volume_24h=${info.volume24H},market_cap=${info.marketCap},percent_change_1h=${info.percentChange1H},
          percent_change_24h=${info.percentChange24H},percent_change_7d=${info.percentChange7D},last_updated=${info.lastUpdated}"""

  implicit val toGetTokenTickerInfo = (r: ResultRow) ⇒
    TokenTickerInfo(tokenId = r <<, name = r <<, symbol = r <<,
      websiteSlug = r <<, market = r <<, rank = r <<, circulatingSupply = r <<,
      totalSupply = r <<, maxSupply = r <<, price = r <<, volume24H = r <<, marketCap = r <<,
      percentChange1H = r <<, percentChange24H = r <<, percentChange7D = r <<, lastUpdated = r <<)

  val cacherTokenTickerInfo = new ProtoBufMessageCacher[GetTokenTickerInfoRes]
  val tokenTickerInfoKey = "TOKEN_TICKER_INFO_"

  override def receive: Receive = {
    case info: TokenTickerInfo ⇒

      saveOrUpdate(info)

    case info: SeqTpro[_] ⇒

      saveOrUpdate(info.t.map(_.asInstanceOf[TokenTickerInfo]): _*)

    case req: GetTokenTickerInfoReq ⇒
      //优先查询缓存，缓存没有再查询数据表并存入缓存
      val res = cacherTokenTickerInfo.getOrElse(buildCacheKey(req.market), Some(600)) {
        val resp: Future[GetTokenTickerInfoRes] =
          sql"""select
             token_id,
             token_name,
             symbol,
             website_slug,
             market,
             cmc_rank,
             circulating_supply,
             total_supply,
             max_supply,
             price,
             volume_24h,
             market_cap,
             percent_change_1h,
             percent_change_24h,
             percent_change_7d,
             last_updated
             from t_token_ticker_info
             where market = ${req.market}
          """.list[TokenTickerInfo].map(GetTokenTickerInfoRes(_))

        resp.map(Some(_))
      }

      res.map {
        case Some(r) ⇒ r
        case _ ⇒ throw new Exception("data in table is null. Please find the reason!")
      } pipeTo sender

  }

  def buildCacheKey(market: String) = {
    s"$tokenTickerInfoKey${market.toUpperCase()}"
  }

}

