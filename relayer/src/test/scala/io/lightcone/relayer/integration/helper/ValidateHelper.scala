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
      orderbookMatcher: Matcher[GetOrderbook.Res],
      ownerFillsMatcher: Matcher[GetUserFills.Res],
      marketFillsMatcher: Matcher[GetMarketFills.Res],
      accountMatcher: Matcher[GetAccount.Res]
    )(
      implicit
      account: Credentials,
      marketPair: MarketPair
    ) = {
    GetOrders.Req(owner = account.getAddress).expect(getOrdersMatcher)
    GetOrderbook
      .Req(marketPair = Some(marketPair))
      .expectUntil(orderbookMatcher)
    GetUserFills.Req(owner = account.getAddress, marketPair = Some(marketPair))
    GetMarketFills.Req(Some(marketPair)).expectUntil(marketFillsMatcher)
    GetAccount
      .Req(address = account.getAddress, allTokens = true)
      .expect(accountMatcher)
  }

}
