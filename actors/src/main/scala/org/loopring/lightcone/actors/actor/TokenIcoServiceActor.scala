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
import com.google.inject.Inject
import org.loopring.lightcone.actors.base
import org.loopring.lightcone.actors.marketcap.DatabaseAccesser
import org.loopring.lightcone.actors.marketcap.{ CacherSettings, ProtoBufMessageCacher }
import org.loopring.lightcone.biz.marketcap._
import org.loopring.lightcone.biz.data._
import org.loopring.lightcone.proto.deployment.TokenIcoServiceSettings

import scala.concurrent.Future

object TokenIcoServiceActor
  extends base.Deployable[TokenIcoServiceSettings] {
  val name = "token_ico_service_actor"

  def getCommon(s: TokenIcoServiceSettings) =
    base.CommonSettings(None, s.roles, s.instances)
}

class TokenIcoServiceActor @Inject() (service: TokenIcoInfoService)(
    implicit
    system: ActorSystem,
    mat: ActorMaterializer,
    session: SlickSession
) extends Actor {

  import system.dispatcher

  implicit val settings = CacherSettings(system.settings.config)

  val cacherTokenIcoInfo = new ProtoBufMessageCacher[GetTokenIcoInfoRes]
  val tokenIcoInfoKey = "TOKEN_ICO_KEY"

  override def receive: Receive = {
    case info: TokenIcoInfo ⇒

      service.saveOrUpdate(info)

    case req: GetTokenIcoInfoReq ⇒
      //优先查询缓存，缓存没有再查询数据表并存入缓存
      val res = cacherTokenIcoInfo.getOrElse(tokenIcoInfoKey, Some(600)) {
        val resp: Future[GetTokenIcoInfoRes] = service.queryTokenIcoInfo()
        resp.map(Some(_))
      }

      res.map {
        case Some(r) ⇒ r
        case _       ⇒ throw new Exception("data in table is null. Please find the reason!")
      } pipeTo sender

  }

}
