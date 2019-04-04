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

class OrderMonitorSpec_Expire
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {
  feature("order monitor ") {
    scenario("order expire") {
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
      val initAvailableAllowance: BigInt = initBaseToken.getAvailableAlloawnce

      Then("submit an order that validUntil = now + 5 seconds")

      val order1 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals()),
        validUntil = (timeProvider.getTimeMillis / 1000).toInt + 5
      )

      SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("submit an order that validUntil = now + 60*60*24")

      val order2 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals()),
        validUntil = timeProvider.getTimeSeconds().toInt + 60 * 60 * 24
      )

      SubmitOrder
        .Req(Some(order2))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("two orders just submitted are STATUS_PENDING")

      defaultValidate(
        getOrdersMatcher =
          containsInGetOrders(STATUS_PENDING, order1.hash, order2.hash),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = initBalance,
            allowance = initAllowance,
            availableBalance = initAvailableAllowance - order1.getAmountS - order1.getFeeParams.getAmountFee - order2.getAmountS - order2.getFeeParams.getAmountFee,
            availableAlloawnce = initAvailableBalance - order1.getAmountS - order1.getFeeParams.getAmountFee - order2.getAmountS - order2.getFeeParams.getAmountFee
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 200
          ), defaultMatcher, defaultMatcher)
        )
      )

      Then("wait 10 seconds for order expire")

      Thread.sleep(10000)

      Then(
        s"${order1.hash} is STATUS_EXPIRED and ${order2.hash} is STATUS_PENDING"
      )

      defaultValidate(
        getOrdersMatcher = containsInGetOrders(STATUS_PENDING, order2.hash) and
          containsInGetOrders(STATUS_EXPIRED, order1.hash),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = initBalance,
            allowance = initAllowance,
            availableBalance = initAvailableAllowance - order2.getAmountS - order2.getFeeParams.getAmountFee,
            availableAlloawnce = initAvailableBalance - order2.getAmountS - order2.getFeeParams.getAmountFee
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 100
          ), defaultMatcher, defaultMatcher)
        )
      )

    }
  }
}
