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

package org.loopring.lightcone.gateway.api.service

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.google.inject.Inject
import org.loopring.lightcone.gateway.api.model.{ BalanceReq, BalanceResp, TokenBalance }
import org.loopring.lightcone.gateway.database.DatabaseAccesser
//import org.loopring.lightcone.proto.actors.{ GetBalanceAndAllowancesReq, GetBalanceAndAllowancesRes }
import akka.pattern.ask
import akka.util.Timeout
import org.loopring.lightcone.gateway.inject.ProxyActor

import scala.concurrent.Future
import scala.concurrent.duration._

class BalanceServiceImpl @Inject() (
  proxy: ProxyActor)(
  implicit
  system: ActorSystem,
  session: SlickSession,
  mat: ActorMaterializer) extends DatabaseAccesser with BalanceService {

  // import session.profile.api._

  implicit val ex = system.dispatcher

  implicit val timeout = Timeout(5 seconds)

  implicit val toTokenInfo = (r: ResultRow) ⇒
    BalanceResp(delegateAddress = r <<, owner = r <<, tokens = Seq.empty)

  // {"jsonrpc": "2.0", "method": "getBalance", "params": {"owner": "haha", "delegateAddress":"dudud"}, "id": 1}
  override def getBalance(req: BalanceReq): Future[BalanceResp] = {

    val accessActor = proxy.ref("ethereum_access_actor").get

    //    val ff = (accessActor ? GetBalanceAndAllowancesReq(address = "hahahhahaha", tokens = Seq("aa", "bb"))).mapTo[GetBalanceAndAllowancesRes]
    //
    //    ff.map { _ ⇒
    //      BalanceResp(delegateAddress = "", owner = req.owner, tokens = Seq(TokenBalance("111", "2222", "333333")))
    //    }

    ???

  }

}
