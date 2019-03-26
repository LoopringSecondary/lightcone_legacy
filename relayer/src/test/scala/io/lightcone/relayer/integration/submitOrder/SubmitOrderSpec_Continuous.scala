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

package io.lightcone.relayer.integration.submitOrder

import io.lightcone.core.ErrorCode.ERR_LOW_BALANCE
import io.lightcone.core.OrderStatus._
import io.lightcone.core._
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

class SubmitOrderSpec_Continuous
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {

  feature("submit  order ") {
    scenario("continuous submit orders") {
      implicit val account = getUniqueAccount()
      Given(
        s"an new account with enough balance and enough allowance: ${account.getAddress}"
      )

      addAccountExpects({
        case req =>
          GetAccount.Res(
            Some(
              AccountBalance(
                address = req.address,
                tokenBalanceMap = req.tokens.map { t =>
                  t -> AccountBalance.TokenBalance(
                    token = t,
                    balance = "1000".zeros(dynamicBaseToken.getDecimals()),
                    allowance = "1000".zeros(dynamicBaseToken.getDecimals())
                  )
                }.toMap
              )
            )
          )
      })

      val getBalanceReq = GetAccount.Req(
        account.getAddress,
        tokens = Seq(dynamicBaseToken.getAddress())
      )
      getBalanceReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )

      When("submit the first order of sell 100 LRC and set fee to 20 LRC.")

      val order1 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "20".zeros(dynamicBaseToken.getDecimals())
      )

      SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))

      defaultValidate(
        getOrdersMatcher = containsInGetOrders(STATUS_PENDING, order1.hash) and
          outStandingMatcherInGetOrders(
            RawOrder.State(
              outstandingAmountS = "100".zeros(dynamicBaseToken.getDecimals()),
              outstandingAmountB = "1".zeros(dynamicBaseToken.getDecimals()),
              outstandingAmountFee = "20".zeros(dynamicBaseToken.getDecimals())
            ),
            order1.hash
          ),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "1000".zeros(dynamicBaseToken.getDecimals()),
            allowance = "1000".zeros(dynamicBaseToken.getDecimals()),
            availableBalance = "880".zeros(dynamicBaseToken.getDecimals()),
            availableAlloawnce = "880".zeros(dynamicBaseToken.getDecimals())
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 100
          ), defaultMatcher, defaultMatcher)
        )
      )

      Then(
        "balance and allowance is 1000 , available balance and available allowance is 880 "
      )

      And("the outstanding amounts is 100")

      And("sell amount of order book is 100")

      val order2 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "500".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "20".zeros(dynamicBaseToken.getDecimals())
      )
      SubmitOrder
        .Req(Some(order2))
        .expect(check((res: SubmitOrder.Res) => res.success))

      When("submit the second order of  sell 500 LRC and fee 20 LRC")

      defaultValidate(
        getOrdersMatcher = containsInGetOrders(STATUS_PENDING, order2.hash) and
          outStandingMatcherInGetOrders(
            RawOrder.State(
              outstandingAmountS = "500".zeros(dynamicBaseToken.getDecimals()),
              outstandingAmountB = "1".zeros(dynamicBaseToken.getDecimals()),
              outstandingAmountFee = "20".zeros(dynamicBaseToken.getDecimals())
            ),
            order2.hash
          ),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "1000".zeros(dynamicBaseToken.getDecimals()),
            allowance = "1000".zeros(dynamicBaseToken.getDecimals()),
            availableBalance = "360".zeros(dynamicBaseToken.getDecimals()),
            availableAlloawnce = "360".zeros(dynamicBaseToken.getDecimals())
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 600
          ), defaultMatcher, defaultMatcher)
        )
      )

      Then(
        "balance and allowance is 1000, available balance and available allowance is 360 "
      )
      And("the outstanding amounts is 500")
      And("sell amount of order book is 600")

      val order3 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "480".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "20".zeros(dynamicBaseToken.getDecimals())
      )
      SubmitOrder
        .Req(Some(order3))
        .expect(check((res: SubmitOrder.Res) => res.success))

      When("submit the third order of  sell 480 LRC and fee 20 LRC")

      defaultValidate(
        getOrdersMatcher = containsInGetOrders(STATUS_PENDING, order3.hash) and
          outStandingMatcherInGetOrders(
            RawOrder.State(
              outstandingAmountS = "480".zeros(dynamicBaseToken.getDecimals()),
              outstandingAmountB = "1".zeros(dynamicBaseToken.getDecimals()),
              outstandingAmountFee = "20".zeros(dynamicBaseToken.getDecimals())
            ),
            order3.hash
          ),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "1000".zeros(dynamicBaseToken.getDecimals()),
            allowance = "1000".zeros(dynamicBaseToken.getDecimals()),
            availableBalance = "0".zeros(dynamicBaseToken.getDecimals()),
            availableAlloawnce =
              "0".zeros(dynamicBaseToken.getMetadata.decimals)
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 945.6
          ), defaultMatcher, defaultMatcher)
        )
      )

      Then(
        "balance and allowance is 1000, available balance and available allowance is 0"
      )
      And("outstanding amounts is 345.6")
      And("sell amount of order book is 945.6")

      When("submit the fourth order of  sell 490 LRC and fee 10 LRC")

      val order4 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "490".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )
      SubmitOrder
        .Req(
          Some(order4)
        )
        .expect(
          check((err: ErrorException) => err.error.code == ERR_LOW_BALANCE)
        )
      Then("submit order failed caused by ERR_LOW_BALANCE")
      defaultValidate(
        getOrdersMatcher =
          containsInGetOrders(STATUS_SOFT_CANCELLED_LOW_BALANCE, order4.hash),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "1000".zeros(dynamicBaseToken.getDecimals()),
            allowance = "1000".zeros(dynamicBaseToken.getDecimals()),
            availableBalance = "0".zeros(dynamicBaseToken.getDecimals()),
            availableAlloawnce = "0".zeros(dynamicBaseToken.getDecimals())
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 945.6
          ), defaultMatcher, defaultMatcher)
        )
      )

      And(
        "balance and allowance is 1000, available balance and available allowance is 0"
      )
      And("sell amount of order book is 945.6")
    }
  }

}
