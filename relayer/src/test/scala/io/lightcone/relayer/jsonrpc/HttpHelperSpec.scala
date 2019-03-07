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

package io.lightcone.relayer.jsonrpc

import akka.actor.{Actor, ActorRef}
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.HttpHelper
import io.lightcone.relayer.support._
import org.scalatest.matchers.{MatchResult, Matcher}

class HttpHelperSpec
    extends CommonSpec
    with EthereumSupport
    with MetadataManagerSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderHandleSupport
    with OrderbookManagerSupport
    with JsonrpcSupport
    with HttpHelper {

  override def beforeAll(): Unit = {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "a example of HttpHelper" must {
    "receive a response " in {
      val method = "get_account"
      val getBalanceReq =
        GetAccount.Req(
          accounts.head.getAddress,
          tokens = Seq(LRC_TOKEN.name, WETH_TOKEN.address)
        )

      getBalanceReq.expect[GetAccount.Res](Matcher { left: GetAccount.Res =>
        MatchResult(
          left.accountBalance.nonEmpty,
          left + " was not odd",
          left + " was odd"
        )
      })
    }
  }
}
