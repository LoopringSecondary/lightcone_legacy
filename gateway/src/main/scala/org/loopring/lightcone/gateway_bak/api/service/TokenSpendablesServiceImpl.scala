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

package org.loopring.lightcone.gateway_bak.api.service

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.scaladsl.SlickSession
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.Inject
import org.loopring.lightcone.gateway_bak.api.model._
import org.loopring.lightcone.gateway_bak.database.DatabaseAccessor
import org.loopring.lightcone.gateway_bak.inject.ProxyActor
//import org.loopring.lightcone.proto.actors.{ GetBalanceAndAllowancesReq, GetBalanceAndAllowancesRes }

class TokenSpendablesServiceImpl @Inject() (proxy: ProxyActor)(
    implicit
    val system: ActorSystem,
    val session: SlickSession,
    val mat: ActorMaterializer
) extends TokenSpendablesService
  with DatabaseAccessor {

  // import session.profile.api._

  implicit val ex = system.dispatcher

  // TODO(Duan): inject this timeout
  implicit val timeout = Timeout(5 seconds)

  implicit val toTokenInfo = (r: ResultRow) ⇒
    TokenSpendablesResp(owner = r <<, delegateAddress = r <<, tokens = Seq.empty)

  // {"jsonrpc": "2.0", "method": "getBalance", "params": {"owner": "haha", "delegateAddress":"dudud"}, "id": 1}
  override def getSpendables(req: TokenSpendablesReq): Future[TokenSpendablesResp] = {

    val accessActor = proxy.providerOf("ethereum_access_actor").get

    //    val ff = (accessActor ? GetBalanceAndAllowancesReq(address = "hahahhahaha", tokens = Seq("aa", "bb"))).mapTo[GetBalanceAndAllowancesRes]
    //
    //    ff.map { _ ⇒
    //      TokenSpendablesResp( owner = req.owner, delegateAddress = "",tokens = Seq(TokenSpendables("111", "2222", "333333")))
    //    }

    ???

  }

}
