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

package io.lightcone.relayer.integration.recovery

import akka.actor.PoisonPill
import io.lightcone.core._
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

class AccountManagerRecoverySpec
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with RecoveryHelper
    with ValidateHelper
    with Matchers {

  feature("test recovery") {
    scenario("account manager recovery") {
      implicit val account1 = getUniqueAccount()
      implicit val account2 = getUniqueAccount()

      addAccountExpects({
        case req =>
          GetAccount.Res(
            Some(
              AccountBalance(
                address = req.address,
                tokenBalanceMap = req.tokens.map { t =>
                  t -> AccountBalance.TokenBalance(
                    token = t,
                    balance = "1000".zeros(18),
                    allowance = "1000".zeros(18)
                  )
                }.toMap
              )
            )
          )
      })

      Given("two accounts with enough balance and allowance")

      When("send an request to make specific MultiAccountManager start")
      GetAccount
        .Req(
          address = account1.getAddress,
          allTokens = true
        )
        .expectUntil(
          check(
            (res: GetAccount.Res) => res.accountBalance.nonEmpty
          )
        )

      When(s"${account1.getAddress} submit an order: sell 100")

      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenS = dynamicBaseToken.getAddress(),
              tokenB = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "100".zeros(dynamicBaseToken.getDecimals())
            )(account1)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s"${account1.getAddress}  submit an order: sell 80")

      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenS = dynamicBaseToken.getAddress(),
              tokenB = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "80".zeros(dynamicBaseToken.getDecimals())
            )(account1)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s" ${account1.getAddress} submit an order: sell 60")

      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenS = dynamicBaseToken.getAddress(),
              tokenB = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "60".zeros(dynamicBaseToken.getDecimals())
            )(account1)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s" ${account1.getAddress} submit an order: buy 150  ")
      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenB = dynamicBaseToken.getAddress(),
              tokenS = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
              amountB = "150".zeros(dynamicBaseToken.getDecimals())
            )(account1)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s" ${account1.getAddress} submit an order: buy 155  ")

      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenB = dynamicBaseToken.getAddress(),
              tokenS = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
              amountB = "155".zeros(dynamicBaseToken.getDecimals())
            )(account1)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))

      defaultValidate(
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            allowance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            availableBalance =
              "751".zeros(dynamicBaseToken.getMetadata.decimals),
            availableAlloawnce =
              "751".zeros(dynamicBaseToken.getMetadata.decimals)
          )
        ) and accountBalanceMatcher(
          dynamicQuoteToken.getAddress(),
          TokenBalance(
            token = dynamicQuoteToken.getAddress(),
            balance = "1000".zeros(dynamicQuoteToken.getMetadata.decimals),
            allowance = "1000".zeros(dynamicQuoteToken.getMetadata.decimals),
            availableBalance =
              "998".zeros(dynamicQuoteToken.getMetadata.decimals),
            availableAlloawnce =
              "998".zeros(dynamicQuoteToken.getMetadata.decimals)
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 240 &&
                res.getOrderbook.buys.map(_.amount.toDouble).sum == 305
          ), defaultMatcher, defaultMatcher)
        )
      )(account1)

      Then("total amount for sell is 240 and total buy amount is 305")

      And(s" ${account1.getAddress} available balance and allowance is 751")

      When(s"${account2.getAddress} submit an order :sell 110")

      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenS = dynamicBaseToken.getAddress(),
              tokenB = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "110".zeros(dynamicBaseToken.getDecimals())
            )(account2)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s"${account2.getAddress} submit an order :sell 90")

      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenS = dynamicBaseToken.getAddress(),
              tokenB = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "90".zeros(dynamicBaseToken.getDecimals())
            )(account2)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s" ${account2.getAddress} submit an order: buy 145 ")
      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenB = dynamicBaseToken.getAddress(),
              tokenS = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
              amountB = "145".zeros(dynamicBaseToken.getDecimals())
            )(account2)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s" ${account2.getAddress} submit an order: buy 130 ")
      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenB = dynamicBaseToken.getAddress(),
              tokenS = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
              amountB = "130".zeros(dynamicBaseToken.getDecimals())
            )(account2)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))

      defaultValidate(
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            allowance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            availableBalance =
              "794".zeros(dynamicBaseToken.getMetadata.decimals),
            availableAlloawnce =
              "794".zeros(dynamicBaseToken.getMetadata.decimals)
          )
        ) and accountBalanceMatcher(
          dynamicQuoteToken.getAddress(),
          TokenBalance(
            token = dynamicQuoteToken.getAddress(),
            balance = "1000".zeros(dynamicQuoteToken.getMetadata.decimals),
            allowance = "1000".zeros(dynamicQuoteToken.getMetadata.decimals),
            availableBalance =
              "998".zeros(dynamicQuoteToken.getMetadata.decimals),
            availableAlloawnce =
              "998".zeros(dynamicQuoteToken.getMetadata.decimals)
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 440 &&
                res.getOrderbook.buys.map(_.amount.toDouble).sum == 580
          ), defaultMatcher, defaultMatcher)
        )
      )(account2)

      Then("total amount for sell is 440 and total buy amount is 580")

      And(s" ${account2.getAddress} available balance and allowance is 794")

      When("send PoisonPill to kill specific multiAccountManagerActor shard")
      getAccountManagerShardActor(account1.getAddress) ! PoisonPill

      GetOrderbook
        .Req(
          size = 20,
          marketPair = Some(dynamicMarketPair)
        )
        .expectUntil(
          check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 440 &&
                res.getOrderbook.buys.map(_.amount.toDouble).sum == 580
          )
        )

      Then("the order book is recovered")

      Thread.sleep(5000)
      GetAccount
        .Req(
          address = account1.getAddress,
          allTokens = true
        )
        .expectUntil(
          accountBalanceMatcher(
            dynamicBaseToken.getAddress(),
            TokenBalance(
              token = dynamicBaseToken.getAddress(),
              balance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
              allowance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
              availableBalance =
                "751".zeros(dynamicBaseToken.getMetadata.decimals),
              availableAlloawnce =
                "751".zeros(dynamicBaseToken.getMetadata.decimals)
            )
          )
        )

      And(s"${account1.getAddress} is recovered")

      GetAccount
        .Req(
          address = account2.getAddress,
          allTokens = true
        )
        .expectUntil(
          accountBalanceMatcher(
            dynamicBaseToken.getAddress(),
            TokenBalance(
              token = dynamicBaseToken.getAddress(),
              balance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
              allowance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
              availableBalance =
                "794".zeros(dynamicBaseToken.getMetadata.decimals),
              availableAlloawnce =
                "794".zeros(dynamicBaseToken.getMetadata.decimals)
            )
          )
        )

      And(s"${account2.getAddress} is recovered")

    }
  }
}
