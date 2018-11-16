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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.google.inject.Inject
import org.loopring.lightcone.proto.market_cap.{ GetTokenListRes, TokenInfo }

import scala.concurrent.Future

class TokenInfoServiceImpl @Inject() (
  implicit
  system: ActorSystem,
  mat: ActorMaterializer,
  session: SlickSession) extends DatabaseAccesser with TokenInfoService {
  import system.dispatcher
  import session.profile.api._

  implicit val toTokenInfo = (r: ResultRow) ⇒
    TokenInfo(protocol = r <<, deny = r <<, isMarket = r <<, symbol = r <<, source = r <<, decimals = r <<)

  override def queryTokenInfo(): Future[GetTokenListRes] = {
    sql"""select protocol,deny,is_market,symbol,source,decimals
              from t_token_info
          """.list[TokenInfo].map(GetTokenListRes(_))
  }

}
