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

package io.lightcone.relayer.integration.matches

import io.lightcone.core.OrderStatus._
import io.lightcone.core._
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

class MatchesSpec_CompleteMatch
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {

  feature("order matches") {
    scenario("complete matches orders") {
      Given("two accounts with enough balance and allowance")
      val account1 = getUniqueAccount()
      val account2 = getUniqueAccount()

      val initAccount = GetAccount
        .Req(
          account1.getAddress,
          tokens = Seq(dynamicBaseToken.getAddress())
        )
        .expectUntil(
          check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
        )

      When(
        s"account1: ${account1.getAddress} submit an order of sell 100 LRC and set fee to 10 LRC."
      )

      val order1 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )(account1)

      SubmitOrder
        .Req(
          rawOrder = Some(order1)
        )
        .expect(
          check(
            (res: SubmitOrder.Res) => res.success
          )
        )

      And(
        s"account2: ${account2.getAddress} submit an order of buy 100 LRC and set fee to 10 LRC."
      )

      val order2 = createRawOrder(
        tokenB = dynamicBaseToken.getAddress(),
        tokenS = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountB = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )(account2)

      SubmitOrder
        .Req(
          rawOrder = Some(order2)
        )
        .expect(
          check((res: SubmitOrder.Res) => res.success)
        )

      Then(
        "order1 and order2 are submitted successfully and status are STATUS_PENDING"
      )

      GetOrderByHash
        .BatchReq(
          hashes = Seq(order1.hash, order2.hash)
        )
        .expect(
          containsInGetOrderByHash(STATUS_PENDING, order1.hash,order2.hash)
        )
    }
  }

}
