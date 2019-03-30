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
import io.lightcone.core.ErrorException
import io.lightcone.core.OrderStatus.STATUS_SOFT_CANCELLED_LOW_BALANCE
import io.lightcone.relayer._
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._

import io.lightcone.relayer.integration._
import org.scalatest._

import scala.math.BigInt

class SubmitOrderSpec_NoBalanceEnoughAllowance
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {

  feature("submit order") {
    scenario("no balance and enough allowance") {
      Given("an new account with no balance and enough allowance")
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
                    balance = BigInt("0"),
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
        accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "0".zeros(dynamicBaseToken.getDecimals()),
            allowance = "1000".zeros(dynamicBaseToken.getDecimals()),
            availableBalance = "0".zeros(dynamicBaseToken.getDecimals()),
            availableAlloawnce = "1000".zeros(dynamicBaseToken.getDecimals())
          )
        )
      )

      When("submit an order.")

      val order = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress()
      )
      SubmitOrder
        .Req(Some(order))
        .expect(
          check((err: ErrorException) => err.error.code == ERR_LOW_BALANCE)
        )

      Then("submit order failed caused by ERR_LOW_BALANCE")

      Then("orders is empty")
      And(
        "allowance and available allowance is 1000, available balance and balance is 0"
      )
      And("order book  is empty")

      defaultValidate(
        getOrdersMatcher =
          containsInGetOrders(STATUS_SOFT_CANCELLED_LOW_BALANCE, order.hash),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "0".zeros(dynamicBaseToken.getMetadata.decimals),
            allowance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            availableBalance = "0".zeros(dynamicBaseToken.getMetadata.decimals),
            availableAlloawnce =
              "1000".zeros(dynamicBaseToken.getMetadata.decimals)
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (orderBookIsEmpty(), defaultMatcher, defaultMatcher)
        )
      )
    }
  }
}
