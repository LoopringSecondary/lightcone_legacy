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

import io.lightcone.core.OrderStatus.STATUS_PENDING
import io.lightcone.core._
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

class SubmitOrderSpec_NotEnoughBalanceEnoughAllowance
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {

  feature("submit an order") {
    scenario("enough balance and not enough allowance") {
      implicit val account = getUniqueAccount()
      Given(
        s"an new account with enough balance and not enough allowance: ${account.getAddress}"
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
                    balance = "30".zeros(dynamicBaseToken.getDecimals()),
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
        check((res: GetAccount.Res) => {
          val ba =
            res.getAccountBalance.tokenBalanceMap(dynamicBaseToken.getAddress())
          NumericConversion.toBigInt(ba.getAllowance) == "1000".zeros(
            dynamicBaseToken.getDecimals()
          ) &&
          NumericConversion.toBigInt(ba.getAvailableAlloawnce) == "1000"
            .zeros(dynamicBaseToken.getDecimals()) &&
          NumericConversion.toBigInt(ba.getBalance) == "30".zeros(
            dynamicBaseToken.getDecimals()
          ) &&
          NumericConversion.toBigInt(ba.getAvailableBalance) == "30"
            .zeros(dynamicBaseToken.getDecimals())
        })
      )

      When("submit an order.")

      val order = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        amountS = "50".zeros(dynamicBaseToken.getDecimals())
      )
      SubmitOrder
        .Req(Some(order))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("submit order successfully")

      defaultValidate(
        getOrdersMatcher = containsInGetOrders(STATUS_PENDING, order.hash) and
          outStandingMatcherInGetOrders(
            RawOrder.State(
              outstandingAmountS = "50".zeros(dynamicBaseToken.getDecimals()),
              outstandingAmountB = "1".zeros(dynamicQuoteToken.getDecimals()),
              outstandingAmountFee = "3".zeros(dynamicBaseToken.getDecimals())
            ),
            order.hash
          ),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "30".zeros(dynamicBaseToken.getMetadata.decimals),
            allowance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            availableBalance = "0".zeros(dynamicBaseToken.getMetadata.decimals),
            availableAlloawnce =
              "970".zeros(dynamicBaseToken.getMetadata.decimals)
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 30
          ), defaultMatcher, defaultMatcher)
        )
      )

      Then("the status of the order just submitted is status pending")
      And(
        "balance = 30, allowance = 1000, availableBalance = 0, availableAllowance = 970"
      )
      And("sell amount of order book is 30")

    }

  }

}
