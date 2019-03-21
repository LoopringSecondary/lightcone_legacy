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

import io.lightcone.core._
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import org.scalatest._

/**
  * 按照不同并且靠近的价格，下单，然后查看orderbook的深度汇总情况
  */
class OrderbookSpec
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("test the levels of orderbook") {
    scenario("test aggregation depth") {

      Given("an account with enough Balance")

      implicit val account = getUniqueAccount()
      val getAccountReq =
        GetAccount.Req(address = account.getAddress, allTokens = true)
      val accountInitRes = getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )

      Then("submit 4 orders with nearby price")
      val orders = Seq(
        createRawOrder(
          tokenS = dynamicMarketPair.baseToken,
          tokenB = dynamicMarketPair.quoteToken,
          amountS = "123456789".zeros(10),
          amountB = "1".zeros(18)
        ),
        createRawOrder(
          tokenS = dynamicMarketPair.baseToken,
          tokenB = dynamicMarketPair.quoteToken,
          amountS = "223456789".zeros(10),
          amountB = "1".zeros(18)
        ),
        createRawOrder(
          tokenS = dynamicMarketPair.baseToken,
          tokenB = dynamicMarketPair.quoteToken,
          amountS = "323456789".zeros(10),
          amountB = "1".zeros(18)
        ),
        createRawOrder(
          tokenS = dynamicMarketPair.baseToken,
          tokenB = dynamicMarketPair.quoteToken,
          amountS = "323455789".zeros(10),
          amountB = "1".zeros(18)
        ),
        createRawOrder(
          tokenS = dynamicMarketPair.quoteToken,
          tokenB = dynamicMarketPair.baseToken,
          amountS = "1".zeros(18),
          amountB = "423455789".zeros(10)
        ),
        createRawOrder(
          tokenS = dynamicMarketPair.quoteToken,
          tokenB = dynamicMarketPair.baseToken,
          amountS = "1".zeros(18),
          amountB = "423456789".zeros(10)
        )
      )
      orders map { order =>
        SubmitOrder
          .Req(Some(order))
          .expectUntil(check((res: SubmitOrder.Res) => res.success))
      }

      Then("check the result of level 1.")
      //根据不同的level需要有不同的汇总
      val orderbookLevel1Matcher = orderBookItemMatcher(
        Seq(
          Orderbook.Item("0.309161", "3.23457", "1.00000"),
          Orderbook.Item("0.309162", "3.23456", "1.00000"),
          Orderbook.Item("0.447514", "2.23457", "1.00000"),
          Orderbook.Item("0.810001", "1.23457", "1.00000")
        ),
        Seq(
          Orderbook.Item("0.236152", "4.23456", "1.00000"),
          Orderbook.Item("0.236151", "4.23457", "1.00000")
        )
      )
      val orderbookLevel1 = GetOrderbook
        .Req(
          size = 100,
          marketPair = Some(dynamicMarketPair)
        )
        .expectUntil(orderbookLevel1Matcher)
      orderbookLevel1 should orderbookLevel1Matcher

      Then("check the result of level 2.")
      val orderbookLevel2Matcher = orderBookItemMatcher(
        Seq(
          Orderbook.Item("0.30917", "6.46913", "2.00000"),
          Orderbook.Item("0.44752", "2.23457", "1.00000"),
          Orderbook.Item("0.81001", "1.23457", "1.00000")
        ),
        Seq(
          Orderbook.Item("0.23615", "8.46913", "2.00000")
        )
      )
      val orderbookLevel2 = GetOrderbook
        .Req(
          level = 1,
          size = 100,
          marketPair = Some(dynamicMarketPair)
        )
        .expectUntil(orderbookLevel2Matcher)
      orderbookLevel2 should orderbookLevel2Matcher
    }
  }
}
