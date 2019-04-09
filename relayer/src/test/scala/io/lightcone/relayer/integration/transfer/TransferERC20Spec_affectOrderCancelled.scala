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
import io.lightcone.core.RawOrder
import io.lightcone.lib.Address
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.helper._
import org.scalatest._
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.lib.NumericConversion._

class TransferERC20Spec_affectOrderCancelled
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with AccountHelper
    with ActivityHelper
    with Matchers {

  feature(
    "transfer out some ERC20 token will affect order's status => STATUS_SOFT_CANCELLED_LOW_BALANCE"
  ) {
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

      Then("check initialize balance")
      val getFromAddressBalanceReq = GetAccount.Req(
        account.getAddress,
        allTokens = true
      )
      val getToAddressBalanceReq = GetAccount.Req(
        to.getAddress,
        allTokens = true
      )
      val fromInitBalanceRes =
        getFromAddressBalanceReq.expectUntil(
          initializeMatcher(dynamicMarketPair)
        )
      val toInitBalanceRes = getToAddressBalanceReq.expectUntil(
        initializeMatcher(dynamicMarketPair)
      )

      When(
        s"submit an order of market: ${dynamicMarketPair.baseToken}-${dynamicMarketPair.quoteToken}."
      )
      val order1 = createRawOrder(
        tokenS = dynamicMarketPair.baseToken,
        tokenB = dynamicMarketPair.quoteToken,
        amountS = "50".zeros(18)
      )(account)
      val submitRes1 = SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))
      info(s"the result of submit order is ${submitRes1.success}")

      Then("available balance should reduce")
      val lrcBalance = fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
        LRC_TOKEN.address
      )
      val lrcExpect = lrcBalance.copy(
        availableBalance = toBigInt(lrcBalance.availableBalance) - toBigInt(
          order1.getFeeParams.amountFee
        ),
        availableAllowance = toBigInt(lrcBalance.availableAllowance) - toBigInt(
          order1.getFeeParams.amountFee
        )
      )
      val baseBalance = fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
        dynamicMarketPair.baseToken
      )
      val baseExpect = baseBalance.copy(
        availableBalance = baseBalance.availableBalance - order1.amountS,
        availableAllowance = toBigInt(baseBalance.availableAllowance) - toBigInt(
          order1.amountS
        )
      )
      getFromAddressBalanceReq.expectUntil(
        balanceMatcher(
          fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
            Address.ZERO.toString
          ),
          fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
            WETH_TOKEN.address
          ),
          lrcExpect,
          baseExpect,
          fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
            dynamicMarketPair.quoteToken
          )
        )
      )

      getToAddressBalanceReq.expectUntil(
        initializeMatcher(dynamicMarketPair)
      )

      When("transfer activities confirmed")
      val transferAmount = "50".zeros(18)
      tokenTransferConfirmedActivities(
        account.getAddress,
        to.getAddress,
        blockNumber,
        txHash,
        dynamicMarketPair.baseToken,
        transferAmount,
        nonce,
        "0".zeros(18),
        "100".zeros(18)
      ).foreach(eventDispatcher.dispatch)

      val baseExpect2 = baseBalance.copy(
        balance = toBigInt(baseBalance.balance) - transferAmount,
        availableBalance = toBigInt(baseBalance.availableBalance) - transferAmount
      )
      val balanceMatcher2 = balanceMatcher(
        fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
          Address.ZERO.toString
        ),
        fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
          WETH_TOKEN.address
        ),
        fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
          LRC_TOKEN.address
        ),
        baseExpect2,
        fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
          dynamicMarketPair.quoteToken
        )
      )

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
            outstandingAmountFee = Some(toAmount("3".zeros(18)))
          ),
          order1.hash
        ),
        balanceMatcher2,
        Map(
          dynamicMarketPair -> (orderBookIsEmpty(),
          userFillsIsEmpty(),
          marketFillsIsEmpty())
        )
      )
    }
  }
}
