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
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.scaladsl.SlickSession
import org.loopring.lightcone.actors.base
import org.loopring.lightcone.actors.marketcap.DatabaseAccesser
import org.loopring.lightcone.actors.marketcap.{ CacherSettings, ProtoBufMessageCacher }
import org.loopring.lightcone.proto.market_cap._
import org.loopring.lightcone.proto.deployment.TokenIcoServiceSettings
import scala.concurrent.Future

object TokenIcoServiceActor
  extends base.Deployable[TokenIcoServiceSettings] {
  val name = "token_ico_service_actor"

  def getCommon(s: TokenIcoServiceSettings) =
    base.CommonSettings(None, s.roles, s.instances)
}

class TokenIcoServiceActor(
  implicit
  system: ActorSystem,
  mat: ActorMaterializer,
  session: SlickSession) extends DatabaseAccesser with Actor {

  import session.profile.api._
  import system.dispatcher

  implicit val settings = CacherSettings(system.settings.config)

  implicit val saveTokenIcoInfo = (info: TokenIcoInfo) ⇒
    sqlu"""INSERT INTO t_token_ico_info(token_address, ico_start_date,
          ico_end_date, hard_cap, soft_cap, token_raised, ico_price, from_country) VALUES(
          ${info.tokenAddress}, ${info.icoStartDate}, ${info.icoEndDate}, ${info.hardCap},
          ${info.softCap}, ${info.tokenRaised}, ${info.icoPrice}, ${info.country}) ON DUPLICATE KEY UPDATE ico_start_date=${info.icoStartDate},
          ico_end_date=${info.icoEndDate},hard_cap=${info.hardCap},soft_cap=${info.softCap},token_raised=${info.tokenRaised},
          ico_price=${info.icoPrice},from_country=${info.country}"""

  implicit val toGetTokenIcoInfo = (r: ResultRow) ⇒
    TokenIcoInfo(tokenAddress = r <<, icoStartDate = r <<, icoEndDate = r <<,
      hardCap = r <<, softCap = r <<, tokenRaised = r <<, icoPrice = r <<, country = r <<)

  val cacherTokenIcoInfo = new ProtoBufMessageCacher[GetTokenIcoInfoRes]
  val tokenIcoInfoKey = "TOKEN_ICO_KEY"

  override def receive: Receive = {
    case info: TokenIcoInfo ⇒

      saveOrUpdate(info)

    case req: GetTokenIcoInfoReq ⇒
      //优先查询缓存，缓存没有再查询数据表并存入缓存
      val res = cacherTokenIcoInfo.getOrElse(tokenIcoInfoKey, Some(600)) {
        val resp: Future[GetTokenIcoInfoRes] =
          sql"""SELECT token_address, ico_start_date, ico_end_date, hard_cap, soft_cap, token_raised,ico_price, from_country
             from t_token_ico_info
          """.list[TokenIcoInfo].map(GetTokenIcoInfoRes(_))

        resp.map(Some(_))
      }

      res.map {
        case Some(r) ⇒ r
        case _ ⇒ throw new Exception("data in table is null. Please find the reason!")
      } pipeTo sender

  }

}
