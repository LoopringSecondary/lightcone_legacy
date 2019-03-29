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

import io.lightcone.core.OrderStatus.STATUS_PENDING
import io.lightcone.core.{Orderbook, RawOrder}
import io.lightcone.lib.Address
import io.lightcone.lib.NumericConversion._
import io.lightcone.relayer._
import io.lightcone.relayer.data.{GetAccount, GetOrderbook, SubmitOrder}
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.relayer.integration.helper._
import org.scalatest._

class TransferERC20Spec_affectOrderBook
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with AccountHelper
    with ActivityHelper
    with Matchers {

  feature("transfer out some ERC20 token will affect order's actual scale") {
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
        getFromAddressBalanceReq.expectUntil(initializeCheck(dynamicMarketPair))
      val toInitBalanceRes = getToAddressBalanceReq.expectUntil(
        initializeCheck(dynamicMarketPair)
      )

      When(
        s"submit an order of market: ${dynamicMarketPair.baseToken}-${dynamicMarketPair.quoteToken}."
      )
      val order1 = createRawOrder(
        tokenS = dynamicMarketPair.baseToken,
        tokenB = dynamicMarketPair.quoteToken,
        "50".zeros(18)
      )(account)
      val submitRes1 = SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))
      info(s"the result of submit order is ${submitRes1.success}")
      Thread.sleep(1000)

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

      Then("available balance should reduce")
      val lrcBalance = fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
        LRC_TOKEN.address
      )
      val lrcMatcher = lrcBalance.copy(
        availableBalance = toBigInt(lrcBalance.availableBalance) - toBigInt(
          order1.getFeeParams.amountFee
        ),
        availableAlloawnce = toBigInt(lrcBalance.availableAlloawnce) - toBigInt(
          order1.getFeeParams.amountFee
        )
      )
      val baseBalance = fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
        dynamicMarketPair.baseToken
      )
      val baseMatcher = baseBalance.copy(
        availableBalance = toBigInt(baseBalance.availableBalance) - toBigInt(
          order1.amountS
        ),
        availableAlloawnce = toBigInt(baseBalance.availableAlloawnce) - toBigInt(
          order1.amountS
        )
      )
      getFromAddressBalanceReq.expectUntil(
        balanceCheck(
          fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
            Address.ZERO.toString
          ),
          fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
            WETH_TOKEN.address
          ),
          lrcMatcher,
          baseMatcher,
          fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
            dynamicMarketPair.quoteToken
          )
        )
      )
      getToAddressBalanceReq.expectUntil(
        balanceCheck(
          toInitBalanceRes.getAccountBalance.tokenBalanceMap(
            Address.ZERO.toString
          ),
          toInitBalanceRes.getAccountBalance.tokenBalanceMap(
            WETH_TOKEN.address
          ),
          toInitBalanceRes.getAccountBalance.tokenBalanceMap(
            LRC_TOKEN.address
          ),
          toInitBalanceRes.getAccountBalance.tokenBalanceMap(
            dynamicMarketPair.baseToken
          ),
          toInitBalanceRes.getAccountBalance.tokenBalanceMap(
            dynamicMarketPair.quoteToken
          )
        )
      )

      When("transfer activities confirmed")
      val transferAmount = "20".zeros(18)
      tokenTransferConfirmedActivities(
        account.getAddress,
        to.getAddress,
        blockNumber,
        txHash,
        dynamicMarketPair.baseToken,
        transferAmount,
        nonce,
        "30".zeros(18),
        "70".zeros(18)
      ).foreach(eventDispatcher.dispatch)
      Thread.sleep(2000)

      val baseAvailable = toBigInt(baseBalance.availableBalance) - toBigInt(
        order1.amountS
      ) - transferAmount
      val baseAvailableMatcher: BigInt =
        if (baseAvailable > 0) baseAvailable else 0
      val availableAllowance =
        if (toBigInt(baseBalance.balance) - transferAmount > order1.amountS) {
          toBigInt(baseBalance.availableAlloawnce) - order1.amountS
        } else {
          toBigInt(baseBalance.availableAlloawnce) - (toBigInt(
            baseBalance.balance
          ) - transferAmount)
        }
      val baseMatcher2 = baseBalance.copy(
        balance = toBigInt(baseBalance.balance) - transferAmount,
        availableBalance = baseAvailableMatcher,
        availableAlloawnce = availableAllowance
      )
      val balanceMatcher = balanceCheck(
        fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
          Address.ZERO.toString
        ),
        fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
          WETH_TOKEN.address
        ),
        lrcMatcher,
        baseMatcher2,
        fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
          dynamicMarketPair.quoteToken
        )
      )

      defaultValidate(
        containsInGetOrders(
          STATUS_PENDING,
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
        balanceMatcher,
        Map(
          dynamicMarketPair -> (not(orderBookIsEmpty()),
          userFillsIsEmpty(),
          marketFillsIsEmpty())
        )
      )

      val orderbookMatcher2 = orderBookItemMatcher(
        Seq(
          Orderbook.Item("0.020000", "30.00000", "0.60000")
        ),
        Seq.empty
      )
      val orderbook2 = GetOrderbook
        .Req(
          size = 100,
          marketPair = Some(dynamicMarketPair)
        )
        .expectUntil(orderbookMatcher2)
      orderbook2 should orderbookMatcher2
    }
  }
}
