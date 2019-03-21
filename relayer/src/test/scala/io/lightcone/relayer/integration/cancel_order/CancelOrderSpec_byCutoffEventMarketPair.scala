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

import io.lightcone.core.MarketPair
import io.lightcone.core.OrderStatus._
import io.lightcone.ethereum.event.{CutoffEvent, EventHeader}
import io.lightcone.ethereum.{BlockHeader, TxStatus}
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._

class CancelOrderSpec_byCutoffEventMarketPair
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("cancel orders of status=STATUS_PENDING") {
    scenario("4: cancel orders of owner by CutoffEvent") {

      Given("an account with enough Balance")
      val anotherTokens = createAndSaveNewMarket(1.0, 1.0)
      val secondBaseToken = anotherTokens(0).getMetadata
      val secondQuoteToken = anotherTokens(1).getMetadata
      val secondMarket =
        MarketPair(secondBaseToken.address, secondQuoteToken.address)

      implicit val account = getUniqueAccount()
      val getAccountReq =
        GetAccount.Req(address = account.getAddress, allTokens = true)
      val accountInitRes = getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )

      val baseTokenBalance =
        accountInitRes.getAccountBalance.tokenBalanceMap(
          dynamicMarketPair.baseToken
        )
      val secondBaseTokenBalance =
        accountInitRes.getAccountBalance.tokenBalanceMap(
          secondMarket.baseToken
        )

      Then(
        "submit two orders that validSince=now-1000 and validSince=now of market:base-quote."
      )
      val order1 =
        createRawOrder(
          tokenS = dynamicMarketPair.baseToken,
          tokenB = dynamicMarketPair.quoteToken,
          tokenFee = dynamicMarketPair.baseToken,
          validSince = timeProvider.getTimeSeconds().toInt - 1000
        )
      val submitRes1 = SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))
      info(
        s"the result of submit first order is ${submitRes1.success}"
      )
      val order3 = createRawOrder(
        tokenS = dynamicMarketPair.baseToken,
        tokenB = dynamicMarketPair.quoteToken,
        tokenFee = dynamicMarketPair.baseToken
      )
      val submitRes3 = SubmitOrder
        .Req(Some(order3))
        .expect(check((res: SubmitOrder.Res) => res.success))
      info(
        s"the result of submit second order is ${submitRes3.success}, ${order3.validSince}"
      )
      Then("submit an order that validSince=now-1000 of second market.")
      val order2 =
        createRawOrder(
          tokenS = secondMarket.baseToken,
          tokenB = secondMarket.quoteToken,
          tokenFee = secondMarket.baseToken,
          validSince = timeProvider.getTimeSeconds().toInt - 1000
        )
      val submitRes2 = SubmitOrder
        .Req(Some(order2))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then(
        s"dispatch CutoffEvent that owner:${account.getAddress} and market:${dynamicMarketPair}."
      )
      val cutoff = CutoffEvent(
        header = Some(
          EventHeader(
            blockHeader = Some(BlockHeader(height = 110)),
            txHash = "0x1111111111111",
            txStatus = TxStatus.TX_STATUS_SUCCESS
          )
        ),
        owner = account.getAddress,
        broker = account.getAddress,
        marketHash = dynamicMarketPair.hashString,
        cutoff = timeProvider.getTimeSeconds().toInt - 500
      )

      eventDispatcher.dispatch(cutoff)

      Thread.sleep(1000)
      Then("check the cancel result.")
      var baseTokenExpectedBalance = baseTokenBalance.copy(
        availableBalance = baseTokenBalance.availableBalance - order3.amountS - order3.getFeeParams.amountFee,
        availableAlloawnce = baseTokenBalance.availableAlloawnce - order3.amountS - order3.getFeeParams.amountFee
      )
      var secondBaseExpectedBalance = secondBaseTokenBalance.copy(
        availableBalance = secondBaseTokenBalance.availableBalance - order2.amountS - order2.getFeeParams.amountFee,
        availableAlloawnce = secondBaseTokenBalance.availableAlloawnce - order2.amountS - order2.getFeeParams.amountFee
      )
      defaultValidate(
        containsInGetOrders(
          STATUS_ONCHAIN_CANCELLED_BY_USER,
          order1.hash
        ) and containsInGetOrders(
          STATUS_PENDING,
          order3.hash,
          order2.hash
        ),
        accountBalanceMatcher(
          dynamicMarketPair.baseToken,
          baseTokenExpectedBalance
        )
          and accountBalanceMatcher(
            secondMarket.baseToken,
            secondBaseExpectedBalance
          ),
        Map(
          dynamicMarketPair -> (not(orderBookIsEmpty()),
          userFillsIsEmpty(),
          marketFillsIsEmpty()),
          MarketPair(secondMarket.baseToken, secondMarket.quoteToken) -> (not(
            orderBookIsEmpty()
          ),
          userFillsIsEmpty(),
          marketFillsIsEmpty())
        )
      )

      Then(
        "submit two new orders, that validSince=now-1000 and valideSince=now"
      )
      val amountS: BigInt = order1.amountS
      val order4 =
        createRawOrder(
          tokenS = dynamicMarketPair.baseToken,
          tokenB = dynamicMarketPair.quoteToken,
          tokenFee = dynamicMarketPair.baseToken,
          amountS = amountS,
          validSince = timeProvider.getTimeSeconds().toInt - 990
        )
      val submitRes4 = SubmitOrder
        .Req(Some(order4))
        .expect(check((res: SubmitOrder.Res) => !res.success))
      info(
        s"submit an order that validSince=${order4.validSince} will be failed."
      )
      val order5 = createRawOrder(
        tokenS = dynamicMarketPair.baseToken,
        tokenB = dynamicMarketPair.quoteToken,
        tokenFee = dynamicMarketPair.baseToken,
        amountS = amountS + BigInt(10)
      )
      val submitRes5 = SubmitOrder
        .Req(Some(order5))
        .expect(check((res: SubmitOrder.Res) => res.success))
      info(
        s"submit an order that validSince=${order5.validSince} will be success."
      )

      Then("submit another order of market:GTO-WETH")
      val order6 =
        createRawOrder(
          tokenS = secondMarket.baseToken,
          tokenB = secondMarket.quoteToken,
          tokenFee = secondMarket.baseToken,
          amountS = amountS + BigInt(10),
          validSince = timeProvider.getTimeSeconds().toInt - 1000
        )
      val submitRes6 = SubmitOrder
        .Req(Some(order6))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("check the result after submit the two new orders.")

      baseTokenExpectedBalance = baseTokenExpectedBalance.copy(
        availableBalance = baseTokenExpectedBalance.availableBalance - order5.amountS - order5.getFeeParams.amountFee,
        availableAlloawnce = baseTokenExpectedBalance.availableAlloawnce - order5.amountS - order5.getFeeParams.amountFee
      )
      secondBaseExpectedBalance = secondBaseExpectedBalance.copy(
        availableBalance = secondBaseExpectedBalance.availableBalance - order6.amountS - order6.getFeeParams.amountFee,
        availableAlloawnce = secondBaseExpectedBalance.availableAlloawnce - order6.amountS - order6.getFeeParams.amountFee
      )
      defaultValidate(
        containsInGetOrders(
          STATUS_ONCHAIN_CANCELLED_BY_USER,
          order1.hash
        ) and containsInGetOrders(
          STATUS_PENDING,
          order2.hash,
          order3.hash,
          order5.hash,
          order6.hash
        ),
        accountBalanceMatcher(
          dynamicMarketPair.baseToken,
          baseTokenExpectedBalance
        )
          and accountBalanceMatcher(
            secondMarket.baseToken,
            secondBaseExpectedBalance
          ),
        Map(
          dynamicMarketPair -> (not(orderBookIsEmpty()),
          userFillsIsEmpty(),
          marketFillsIsEmpty()),
          MarketPair(secondMarket.baseToken, secondMarket.quoteToken) -> (not(
            orderBookIsEmpty()
          ),
          userFillsIsEmpty(),
          marketFillsIsEmpty())
        )
      )

    }
  }
}
