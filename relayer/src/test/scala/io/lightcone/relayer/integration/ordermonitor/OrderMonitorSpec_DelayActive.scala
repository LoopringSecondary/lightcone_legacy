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

package io.lightcone.relayer.integration.ordermonitor

import io.lightcone.core.OrderStatus._
import io.lightcone.core._
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

class OrderMonitorSpec_DelayActive
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {

  feature("order monitor ") {
    scenario("order delay active") {
      Given("an account with enough balance and allowance")
      implicit val account = getUniqueAccount()

      val getAccountReq = GetAccount.Req(
        address = account.getAddress,
        allTokens = true
      )
      val accountInitRes = getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )

      val initBaseToken =
        accountInitRes.getAccountBalance.tokenBalanceMap(
          dynamicBaseToken.getAddress()
        )
      val initBalance: BigInt = initBaseToken.getBalance
      val initAllowance: BigInt = initBaseToken.getAllowance
      val initAvailableBalance: BigInt = initBaseToken.getAvailableBalance
      val initAvailableAllowance: BigInt = initBaseToken.getAvailableAllowance

      Then("submit an order that validSince = now + 5 seconds")

      val order1 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals()),
        validSince = timeProvider.getTimeSeconds().toInt + 5
      )

      SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("submit an order that validSince = now")

      val order2 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )

      SubmitOrder
        .Req(Some(order2))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("order is STATUS_PENDING_ACTIVE and order2 is STATUS_PENDING")

      defaultValidate(
        getOrdersMatcher =
          containsInGetOrders(STATUS_PENDING, order2.hash) and containsInGetOrders(
            STATUS_PENDING_ACTIVE,
            order1.hash
          ),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = initBalance,
            allowance = initAllowance,
            availableBalance = initAvailableAllowance - order2.getAmountS - order2.getFeeParams.getAmountFee,
            availableAllowance = initAvailableBalance - order2.getAmountS - order2.getFeeParams.getAmountFee
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 100
          ), defaultMatcher, defaultMatcher)
        )
      )

      Then("wait 5 seconds for order1 to be active and resubmitted")

      Thread.sleep(5000)

      GetOrders
        .Req(owner = account.getAddress)
        .expectUntil(
          check(
            (res: GetOrders.Res) =>
              res.orders.head.getState.status == STATUS_PENDING
          )
        )

      Then(
        s"${order1.hash} and ${order2.hash} are STATUS_PENDING"
      )

      defaultValidate(
        getOrdersMatcher = containsInGetOrders(
          STATUS_PENDING,
          order1.hash,
          order2.hash
        ),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = initBalance,
            allowance = initAllowance,
            availableBalance = initAvailableAllowance - order1.getAmountS - order1.getFeeParams.getAmountFee - order2.getAmountS - order2.getFeeParams.getAmountFee,
            availableAllowance = initAvailableBalance - order1.getAmountS - order1.getFeeParams.getAmountFee - order2.getAmountS - order2.getFeeParams.getAmountFee
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 200
          ), defaultMatcher, defaultMatcher)
        )
      )

    }
  }

}
