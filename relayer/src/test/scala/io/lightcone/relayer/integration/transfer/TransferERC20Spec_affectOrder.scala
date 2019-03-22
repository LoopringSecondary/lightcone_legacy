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

import io.lightcone.core.OrderStatus.STATUS_ONCHAIN_CANCELLED_BY_USER
import io.lightcone.core.RawOrder
import io.lightcone.lib.Address
import io.lightcone.lib.NumericConversion._
import io.lightcone.relayer._
import io.lightcone.relayer.data.{GetAccount, GetActivities, SubmitOrder}
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.helper._
import org.scalatest._
import io.lightcone.lib.NumericConversion.toAmount

class TransferERC20Spec_affectOrder
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with AccountHelper
    with ActivityHelper
    with Matchers {

  feature("transfer ERC20 affect order") {
    scenario("transfer ERC20") {
      implicit val account = getUniqueAccount()
      val txHash =
        "0xbc6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      val to = getUniqueAccount()
      val blockNumber = 987L
      val nonce = 11L

      Given("initialize balance")
      mockAccountWithFixedBalance(account.getAddress, dynamicMarketPair)
      mockAccountWithFixedBalance(to.getAddress, dynamicMarketPair)

      val getFromAddressBalanceReq = GetAccount.Req(
        account.getAddress,
        allTokens = true
      )
      val getToAddressBalanceReq = GetAccount.Req(
        to.getAddress,
        allTokens = true
      )

      When("submit an order of market: base-quote.")
      val order1 = createRawOrder(
        tokenS = dynamicMarketPair.baseToken,
        tokenB = dynamicMarketPair.quoteToken,
        "50".zeros(18)
      )
      val submitRes1 = SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))
      info(s"the result of submit order is ${submitRes1.success}")
      Thread.sleep(1000)

      Then("available balance should reduce")
      getFromAddressBalanceReq.expectUntil(
        check((res: GetAccount.Res) => {
          val balanceOpt = res.accountBalance
          val ethBalance = toBigInt(
            balanceOpt.get.tokenBalanceMap(Address.ZERO.toString).balance.get
          )
          val ethAvailableBalance = toBigInt(
            balanceOpt.get
              .tokenBalanceMap(Address.ZERO.toString)
              .availableBalance
              .get
          )
          val baseBalance = toBigInt(
            balanceOpt.get
              .tokenBalanceMap(dynamicMarketPair.baseToken)
              .balance
              .get
          )
          val baseAvailableBalance = toBigInt(
            balanceOpt.get
              .tokenBalanceMap(dynamicMarketPair.baseToken)
              .availableBalance
              .get
          )
          ethBalance == "20"
            .zeros(18) && ethBalance == ethAvailableBalance && baseBalance == "50"
            .zeros(18) && "0".zeros(18) == baseAvailableBalance
        })
      )

      When("activities confirmed")
      tokenTransferConfirmedActivities(
        account.getAddress,
        to.getAddress,
        blockNumber,
        txHash,
        dynamicMarketPair.baseToken,
        "50".zeros(18),
        nonce,
        "0".zeros(18),
        "100".zeros(18)
      ).foreach(eventDispatcher.dispatch)
      Thread.sleep(1000)

      val balanceExpect = (res: GetAccount.Res) => {
        val balanceOpt = res.accountBalance
        val ethBalance = toBigInt(
          balanceOpt.get.tokenBalanceMap(Address.ZERO.toString).balance.get
        )
        val ethAvailableBalance = toBigInt(
          balanceOpt.get
            .tokenBalanceMap(Address.ZERO.toString)
            .availableBalance
            .get
        )
        val baseBalance = toBigInt(
          balanceOpt.get
            .tokenBalanceMap(dynamicMarketPair.baseToken)
            .balance
            .get
        )
        val baseAvailableBalance = toBigInt(
          balanceOpt.get
            .tokenBalanceMap(dynamicMarketPair.baseToken)
            .availableBalance
            .get
        )
        ethBalance == "20"
          .zeros(18) && ethBalance == ethAvailableBalance && baseBalance == "0"
          .zeros(18) && baseBalance == baseAvailableBalance
      }

      defaultValidate(
        containsInGetOrders(
          STATUS_ONCHAIN_CANCELLED_BY_USER,
          order1.hash
        ) and outStandingMatcherInGetOrders(
          RawOrder.State(
            outstandingAmountS = Some(
              toAmount(0)
            ),
            outstandingAmountB = Some(toAmount(0)),
            outstandingAmountFee = Some(toAmount(0))
          ),
          order1.hash
        ),
        be(balanceExpect),
        Map(
          dynamicMarketPair -> (orderBookIsEmpty(),
          userFillsIsEmpty(),
          marketFillsIsEmpty())
        )
      )

    }
  }
}
