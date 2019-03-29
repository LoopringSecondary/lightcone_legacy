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
import io.lightcone.core.OrderStatus.STATUS_SOFT_CANCELLED_LOW_BALANCE
import io.lightcone.core._
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

import scala.math.BigInt

class SubmitOrderSpec_EnoughBalanceNoAllowance
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {

  feature("submit  order ") {
    scenario("enough balance and no allowance") {
      Given(s"an new account with enough balance and no allowance")
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
                    allowance = BigInt(0)
                  )
                }.toMap
              )
            )
          )
      })

      GetAccount
        .Req(
          account.getAddress,
          tokens = Seq(dynamicBaseToken.getAddress())
        )
        .expectUntil(
          check((res: GetAccount.Res) => {
            val lrc_ba =
              res.getAccountBalance
                .tokenBalanceMap(dynamicBaseToken.getAddress())
            NumericConversion.toBigInt(lrc_ba.getAllowance) == 0 &&
            NumericConversion.toBigInt(lrc_ba.getAvailableAlloawnce) == 0 &&
            NumericConversion.toBigInt(lrc_ba.getBalance) == "1000".zeros(
              dynamicBaseToken.getMetadata.decimals
            ) &&
            NumericConversion.toBigInt(lrc_ba.getAvailableBalance) == "1000"
              .zeros(dynamicBaseToken.getMetadata.decimals)
          })
        )

      When("submit an order.")
      val order1 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress()
      )
      SubmitOrder
        .Req(Some(order1))
        .expect(
          check(
            (err: ErrorException) => err.error.code == ERR_LOW_BALANCE
          )
        )

      Then("submit order failed caused by ERR_LOW_BALANCE")

      Then(
        "status of order just submitted is status_soft_cancelled_low_balance"
      )
      And(
        "balance and availableBalance is 1000, allowance and availableAllowance is 0"
      )
      And("order book is empty")

      defaultValidate(
        getOrdersMatcher =
          containsInGetOrders(STATUS_SOFT_CANCELLED_LOW_BALANCE, order1.hash),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            allowance = "0".zeros(dynamicBaseToken.getMetadata.decimals),
            availableBalance =
              "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            availableAlloawnce =
              "0".zeros(dynamicBaseToken.getMetadata.decimals)
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (orderBookIsEmpty(), defaultMatcher, defaultMatcher)
        )
      )
    }
  }

}
