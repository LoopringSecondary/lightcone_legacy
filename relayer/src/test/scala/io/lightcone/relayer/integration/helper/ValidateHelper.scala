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
import io.lightcone.core.MarketPair
import io.lightcone.relayer.data._
import org.scalatest.matchers.Matcher
import org.web3j.crypto.Credentials

trait ValidateHelper {
  me: RpcHelper =>

  def defaultValidate(
      getOrdersMatcher: Matcher[GetOrders.Res],
      accountMatcher: Matcher[GetAccount.Res],
      marketMatchers: Map[MarketPair, (Matcher[GetOrderbook.Res], Matcher[
            GetUserFills.Res
          ], Matcher[GetMarketFills.Res])]
    )(
      implicit
      account: Credentials
    ) = {
    GetOrders.Req(owner = account.getAddress).expect(getOrdersMatcher)
    GetAccount
      .Req(address = account.getAddress, allTokens = true)
      .expect(accountMatcher)
    marketMatchers.map {
      case (pair, (orderbookMatcher, userFillsMatcher, marketFillsMatcher)) =>
        GetOrderbook
          .Req(size = 100, marketPair = Some(pair))
          .expectUntil(orderbookMatcher)
        GetUserFills
          .Req(
            owner = Some(account.getAddress),
            market = Some(MarketFilter(marketPair = Some(pair)))
          )
          .expectUntil(userFillsMatcher)
        GetMarketFills.Req(Some(pair)).expectUntil(marketFillsMatcher)
    }
  }

}
