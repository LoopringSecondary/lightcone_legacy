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

package io.lightcone.relayer.integration

import io.lightcone.core.OrderStatus.STATUS_SOFT_CANCELLED_LOW_BALANCE
import io.lightcone.core._
import io.lightcone.ethereum.TxStatus
import io.lightcone.lib.NumericConversion.toAmount
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.helper._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._

class ApproveFeeToZeroSpec_affectOrder
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with AccountHelper
    with ActivityHelper
    with Matchers {

  feature("Loopring protocol approved sell token to zero will affect orders") {
    scenario("approve test") {
      implicit val account = getUniqueAccount()
      val txHash =
        "0xbc6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      val blockNumber = 987L
      val nonce = 11L

      Given("initialize balance")
      mockAccountWithFixedBalance(account.getAddress, dynamicMarketPair)

      Then("check initialize balance")
      val getFromAddressBalanceReq = GetAccount.Req(
        account.getAddress,
        allTokens = true
      )
      getFromAddressBalanceReq.expectUntil(
        initializeMatcher(dynamicMarketPair)
      )

      When(
        s"submit an order of market: ${dynamicMarketPair.baseToken}-${dynamicMarketPair.quoteToken}."
      )
      val order1 = createRawOrder(
        tokenS = dynamicMarketPair.baseToken,
        tokenB = dynamicMarketPair.quoteToken,
        amountS = "50".zeros(18),
        amountFee = "100".zeros(18)
      )(account)
      val submitRes1 = SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))
      info(s"the result of submit order is ${submitRes1.success}")

      Then("check orderbook")
      val orderbookMatcher1 = orderBookItemMatcher(
        Seq(
          Orderbook.Item("0.020000", "50.00000", "1.00000")
        ),
        Seq.empty
      )
      val orderbook1 = GetOrderbook
        .Req(
          size = 100,
          marketPair = Some(dynamicMarketPair)
        )
        .expectUntil(orderbookMatcher1)
      orderbook1 should orderbookMatcher1

      When("Loopring approve fee token to zero activities confirmed")
      loopringApproveConfirmedActivities(
        account.getAddress,
        blockNumber,
        txHash,
        LRC_TOKEN.address,
        BigInt(0),
        nonce
      ).foreach(eventDispatcher.dispatch)

      Then("verify confirmed activity")
      GetActivities
        .Req(account.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 1 && res.activities.head.txStatus == TxStatus.TX_STATUS_SUCCESS
          })
        )

      Then("verify balance and allowance")
      defaultValidate(
        containsInGetOrders(
          STATUS_SOFT_CANCELLED_LOW_BALANCE,
          order1.hash
        ) and outStandingMatcherInGetOrders(
          RawOrder.State(
            outstandingAmountS = Some(
              toAmount("50".zeros(18))
            ),
            outstandingAmountB = Some(toAmount("1".zeros(18))),
            outstandingAmountFee = Some(toAmount("100".zeros(18)))
          ),
          order1.hash
        ),
        initializeMatcher(dynamicMarketPair),
        Map(
          dynamicMarketPair -> (orderBookIsEmpty(),
          userFillsIsEmpty(),
          marketFillsIsEmpty())
        )
      )
    }
  }
}
