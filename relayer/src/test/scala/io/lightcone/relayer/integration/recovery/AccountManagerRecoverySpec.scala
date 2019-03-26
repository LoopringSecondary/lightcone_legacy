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
import akka.pattern._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas.LRC_TOKEN
import io.lightcone.relayer.integration._
import org.scalatest._

import scala.concurrent.Await

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
      implicit val account3 = getUniqueAccount()

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

      Given("three accounts with enough balance and allowance")

      GetAccount
        .Req(
          address = account1.getAddress,
          allTokens = true
        )
        .expectUntil(
          check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
        )

      When("submit an order: sell 100")

      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenS = dynamicBaseToken.getAddress(),
              tokenB = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "100".zeros(dynamicBaseToken.getMetadata.decimals)
            )(account1)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))

      And("submit an order: sell 80")

      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenS = dynamicBaseToken.getAddress(),
              tokenB = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "80".zeros(dynamicBaseToken.getMetadata.decimals)
            )(account1)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))


      And("submit an order: sell 60")

      SubmitOrder
        .Req(
          Some(
            createRawOrder(
              tokenS = dynamicBaseToken.getAddress(),
              tokenB = dynamicQuoteToken.getAddress(),
              tokenFee = dynamicBaseToken.getAddress(),
              amountS = "60".zeros(dynamicBaseToken.getMetadata.decimals)
            )(account1)
          )
        )
        .expect(check((res: SubmitOrder.Res) => res.success))


      GetOrderbook
        .Req(
          size = 20,
          marketPair = Some(dynamicMarketPair)
        )
        .expectUntil(
          check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 240
          )
        )
      Then("total amount for sell is 240")

      val shardActor = getAccountManagerShardActor(account1.getAddress)

      println(shardActor.toString())

      getAccountManagerShardActor(account1.getAddress) ! PoisonPill

      Thread.sleep(1000)

      val f = (getAccountManagerShardActor(account1.getAddress) ? GetAccount
        .Req(
          address = account1.getAddress,
          allTokens = true
        )).mapAs[GetAccount.Res]

      Await.result(f, timeout.duration)

      GetOrderbook
        .Req(
          size = 20,
          marketPair = Some(dynamicMarketPair)
        )
        .expectUntil(
          check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 240
          )
        )
    }
  }
}
