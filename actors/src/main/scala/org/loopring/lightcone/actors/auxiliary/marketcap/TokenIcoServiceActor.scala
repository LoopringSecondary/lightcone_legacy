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

package org.loopring.lightcone.actors.auxiliary.marketcap

import akka.actor.{ Actor, ActorSystem }
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.scaladsl.SlickSession
import javax.inject.Inject
import org.loopring.lightcone.auxiliary.marketcap.reader.TokenIcoInfoService
import org.loopring.lightcone.ethereum.cache.ProtoBufMessageCacher
import org.loopring.lightcone.proto.auxiliary.{ XGetTokenIcoInfoReq, XGetTokenIcoInfoRes, XTokenIcoInfo }
import redis.RedisCluster

class TokenIcoServiceActor @Inject() (tokenIcoInfoService: TokenIcoInfoService)(
    implicit
    system: ActorSystem,
    mat: ActorMaterializer,
    redis: RedisCluster,
    session: SlickSession
) extends Actor {

  import system.dispatcher

  val cacher = new ProtoBufMessageCacher[XGetTokenIcoInfoRes]
  val tokenIcoInfoKey = "TOKEN_ICO_KEY"

  override def receive: Receive = {
    case info: XTokenIcoInfo ⇒
      tokenIcoInfoService.saveOrUpdate(info)

    // 先不考虑按照Token地址查询具体TokenICO的情况，返回所有ICO信息
    case req: XGetTokenIcoInfoReq ⇒
      val res = cacher.getOrElse(tokenIcoInfoKey, Some(600)) {
        tokenIcoInfoService.queryAllXTokenIcoInfo().map(r ⇒ Some(r))
      }

      res.map {
        case Some(r) ⇒
          cacher.put(tokenIcoInfoKey, r, Some(600))
          r
        case _ ⇒ throw new Exception("data in table is null. Please find the reason!")
      } pipeTo sender
  }

}
