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

import io.lightcone.core.ErrorCode.ERR_INTERNAL_UNKNOWN
import io.lightcone.core.OrderStatus._
import io.lightcone.core._
import io.lightcone.ethereum.TxStatus.TX_STATUS_SUCCESS
import io.lightcone.ethereum.event._
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

class SubmitOrderSpec_reSubmitCancelled
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {

  feature("submit  order ") {
    scenario("enough balance and enough allowance") {
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

      When("submit an order.")

      val order = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "40".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )
      SubmitOrder
        .Req(Some(order))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("submit order successfully")

      defaultValidate(
        getOrdersMatcher = containsInGetOrders(STATUS_PENDING, order.hash) and
          outStandingMatcherInGetOrders(
            RawOrder.State(
              outstandingAmountS =
                "40".zeros(dynamicBaseToken.getMetadata.decimals),
              outstandingAmountB =
                "1".zeros(dynamicQuoteToken.getMetadata.decimals),
              outstandingAmountFee =
                "10".zeros(dynamicBaseToken.getMetadata.decimals)
            ),
            order.hash
          ),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            allowance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            availableBalance =
              "950".zeros(dynamicBaseToken.getMetadata.decimals),
            availableAlloawnce =
              "950".zeros(dynamicBaseToken.getMetadata.decimals)
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 40
          ), defaultMatcher, defaultMatcher)
        )
      )

      And("the status of the order just submitted is status pending")
      And(
        "balance and allowance is 1000, available balance and available allowance is 950"
      )
      And(s" sell amount of order book is 40")

      val cancelEvent = OrdersCancelledOnChainEvent(
        owner = account.getAddress,
        header = Some(EventHeader(txStatus = TX_STATUS_SUCCESS)),
        orderHashes = Seq(order.hash)
      )
      eventDispatcher.dispatch(cancelEvent)
      GetOrders
        .Req(owner = account.getAddress)
        .expectUntil(
          check((res: GetOrders.Res) => {
            res.orders.head.getState.status.isStatusOnchainCancelledByUser
          })
        )
      When("cancel the order by on chain event")

      And("resubmit the order")

      SubmitOrder
        .Req(Some(order))
        .expect(
          check((err: ErrorException) => err.error.code == ERR_INTERNAL_UNKNOWN)
        )

      Then("submit order failed caused by ")

      defaultValidate(
        getOrdersMatcher =
          containsInGetOrders(STATUS_ONCHAIN_CANCELLED_BY_USER, order.hash),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            allowance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            availableBalance =
              "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            availableAlloawnce =
              "1000".zeros(dynamicBaseToken.getMetadata.decimals)
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (orderBookIsEmpty(), defaultMatcher, defaultMatcher)
        )
      )

      And("the status of the order just submitted is status pending")
      And(
        "balance and allowance is 1000, available balance and available allowance is 100"
      )
      And("order book is empty")

    }
  }

}
