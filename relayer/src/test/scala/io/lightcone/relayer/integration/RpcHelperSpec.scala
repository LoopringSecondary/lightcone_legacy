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

package io.lightcone.relayer.integration
import io.lightcone.relayer.data.GetAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.support.{accounts, LRC_TOKEN, WETH_TOKEN}
import org.scalatest.Matchers

class RpcHelperSpec extends CommonHelper with Matchers {

  "an example of HttpHelper" must {
    "receive a response " in {
      println("###### RpcHelperSpec ####")
      val method = "get_account"
      val getBalanceReq =
        GetAccount.Req(
          accounts.head.getAddress,
          tokens = Seq(LRC_TOKEN.name, WETH_TOKEN.address)
        )

      getBalanceReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )
    }
  }

}
