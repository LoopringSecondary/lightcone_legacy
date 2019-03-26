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

import io.lightcone.core.ErrorCode.ERR_ORDER_VALIDATION_INVALID_CUTOFF
import io.lightcone.core.OrderStatus._
import io.lightcone.core._
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

import scala.math.BigInt

class SubmitOrderSpec_OwnerCutoffTradingPair
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {

  feature("submit  order ") {
    scenario("lrc-weth market cutoff is not zero and others' are zero") {
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

      addCutoffsExpects({
        case req =>
          BatchGetCutoffs.Res(
            req.reqs.map { r =>
              if (r.marketHash == dynamicMarketPair.hashString)
                GetCutoff.Res(
                  r.broker,
                  r.owner,
                  r.marketHash,
                  BigInt(timeProvider.getTimeSeconds())
                )
              else
                GetCutoff.Res(r.broker, r.owner, r.marketHash, BigInt(0))
            }
          )
      })

      When("submit the an order that valid since is smaller than cutoff")

      val order1 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        validSince = (timeProvider.getTimeSeconds() - 1).toInt
      )

      SubmitOrder
        .Req(Some(order1))
        .expect(
          check(
            (err: ErrorException) =>
              err.error.code == ERR_ORDER_VALIDATION_INVALID_CUTOFF
          )
        )

      Then(
        "submit order failed caused by ERR_ORDER_VALIDATION_INVALID_CUTOFF"
      )

      defaultValidate(
        getOrdersMatcher = check((res: GetOrders.Res) => res.orders.isEmpty),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "1000".zeros(dynamicBaseToken.getDecimals()),
            allowance = "1000".zeros(dynamicBaseToken.getDecimals()),
            availableBalance = "1000".zeros(dynamicBaseToken.getDecimals()),
            availableAlloawnce = "1000".zeros(dynamicBaseToken.getDecimals())
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (orderBookIsEmpty(), defaultMatcher, defaultMatcher)
        )
      )

      And("orders is empty")
      And(
        "balance and allowance is 1000, available balance and available allowance is 1000"
      )
      And("order book is empty")

      When("submit the an order that valid since is bigger than cutoff")

      val order2 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        validSince = (timeProvider.getTimeSeconds() + 1).toInt
      )
      SubmitOrder
        .Req(Some(order2))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then(
        "submit an order successfully"
      )

      defaultValidate(
        getOrdersMatcher =
          containsInGetOrders(STATUS_PENDING_ACTIVE, order2.hash),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "1000".zeros(dynamicBaseToken.getDecimals()),
            allowance = "1000".zeros(dynamicBaseToken.getDecimals()),
            availableBalance = "1000".zeros(dynamicBaseToken.getDecimals()),
            availableAlloawnce = "1000".zeros(dynamicBaseToken.getDecimals())
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (orderBookIsEmpty(), defaultMatcher, defaultMatcher)
        )
      )

      And("order status is STATUS_PENDING_ACTIVE")
      And(
        "balance and allowance is 1000, available balance and available allowance is 1000"
      )
      And("order book is empty")

      When("submit an order of another market")
      val tokens1 =
        createAndSaveNewMarket()
      val baseToken1 = tokens1.head
      val quoteToken1 = tokens1(1)
      val market1 =
        MarketPair(baseToken1.getAddress(), quoteToken1.getAddress())
      val order3 = createRawOrder(
        tokenS = baseToken1.getAddress(),
        tokenB = quoteToken1.getAddress(),
        tokenFee = baseToken1.getAddress()
      )

      SubmitOrder
        .Req(Some(order3))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("submit order successfully")
      defaultValidate(
        getOrdersMatcher = containsInGetOrders(STATUS_PENDING, order3.hash),
        accountMatcher = accountBalanceMatcher(
          baseToken1.getAddress(),
          TokenBalance(
            token = baseToken1.getAddress(),
            balance = "1000".zeros(baseToken1.getDecimals()),
            allowance = "1000".zeros(baseToken1.getDecimals()),
            availableBalance = "987".zeros(baseToken1.getDecimals()),
            availableAlloawnce = "987".zeros(baseToken1.getDecimals())
          )
        ),
        marketMatchers = Map(
          market1 -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 10
          ), defaultMatcher, defaultMatcher)
        )
      )
      And("order status is STATUS_PENDING")
      And(
        "balance and allowance is 1000, available balance and available allowance is 987"
      )
      And("sum of order book sell amount is 10")

    }
  }

}
