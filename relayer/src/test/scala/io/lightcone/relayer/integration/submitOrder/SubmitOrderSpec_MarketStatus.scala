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

import io.lightcone.core.ErrorCode.ERR_INVALID_MARKET
import io.lightcone.core._
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

class SubmitOrderSpec_MarketStatus
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {

  feature("submit order") {
    scenario("submit order according to different status of market") {
      Given("an account has enough balance and allowance")
      implicit val account = getUniqueAccount()

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

      Then("create a new market of status READONLY")
      val tokens1 =
        createAndSaveNewMarket(status = MarketMetadata.Status.READONLY)
      val baseToken1 = tokens1.head
      val quoteToken1 = tokens1(1)
      val market1 =
        MarketPair(baseToken1.getAddress(), quoteToken1.getAddress())

      When("submit an order of readonly market")
      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenS = market1.baseToken,
              tokenB = market1.quoteToken
            )
          )
        )
        .expect(
          check(
            (error: ErrorException) => error.error.code == ERR_INVALID_MARKET
          )
        )
      Then("submit order failed caused by ERR_INVALID_MARKET")

      Then("orders is empty")
      And(
        "balance and allowance is 1000, available balance and allowance is 1000 "
      )
      And("order book  is empty")

      defaultValidate(
        getOrdersMatcher = check((res: GetOrders.Res) => res.orders.isEmpty),
        accountMatcher = accountBalanceMatcher(
          baseToken1.getAddress(),
          TokenBalance(
            token = baseToken1.getAddress(),
            balance = "1000".zeros(baseToken1.getMetadata.decimals),
            allowance = "1000".zeros(baseToken1.getMetadata.decimals),
            availableBalance = "1000".zeros(baseToken1.getMetadata.decimals),
            availableAlloawnce = "1000".zeros(baseToken1.getMetadata.decimals)
          )
        ),
        marketMatchers = Map(
          market1 -> (orderBookIsEmpty(), defaultMatcher, defaultMatcher)
        )
      )
      Then("create a new market of status TERMINATED")

      val tokens2 =
        createAndSaveNewMarket(status = MarketMetadata.Status.TERMINATED)
      val baseToken2 = tokens2.head
      val quoteToken2 = tokens2(1)
      val market2 =
        MarketPair(baseToken1.getAddress(), quoteToken2.getAddress())

      When("submit an order of terminated market")

      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenS = market2.baseToken,
              tokenB = market2.quoteToken
            )
          )
        )
        .expect(
          check(
            (error: ErrorException) => error.error.code == ERR_INVALID_MARKET
          )
        )
      Then("submit order failed caused by ERR_INVALID_MARKET")

      Then("orders is empty")
      And(
        "balance and allowance is 1000, available balance and allowance is 1000 "
      )
      And("order book  is empty")

      defaultValidate(
        getOrdersMatcher = check((res: GetOrders.Res) => res.orders.isEmpty),
        accountMatcher = accountBalanceMatcher(
          baseToken2.getAddress(),
          TokenBalance(
            token = baseToken2.getAddress(),
            balance = "1000".zeros(baseToken2.getMetadata.decimals),
            allowance = "1000".zeros(baseToken2.getMetadata.decimals),
            availableBalance = "1000".zeros(baseToken2.getMetadata.decimals),
            availableAlloawnce = "1000".zeros(baseToken2.getMetadata.decimals)
          )
        ),
        marketMatchers = Map.empty
      )

    }

  }

}
