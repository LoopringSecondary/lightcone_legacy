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

package io.lightcone.relayer.integration.orders.submitOrders

import akka.pattern._
import io.lightcone.core._
import io.lightcone.lib.Address
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers.check
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.relayer.integration._
import org.scalatest._

import scala.concurrent.Await

class SubmitOrderSpec_MarketStatus
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with Matchers {

  feature("submit order") {
    scenario("submit order accoring to different status of market") {
      implicit val account = getUniqueAccount()

      val token1 = Address.normalize(getUniqueAccount().getAddress)
      val token2 = Address.normalize(getUniqueAccount().getAddress)
      val token3 = Address.normalize(getUniqueAccount().getAddress)

      Given("an account has enough balance and allowance")

      And("set market status")

      Await.result(
        dbModule.tokenMetadataDal.saveTokenMetadatas(
          Seq(
            LRC_TOKEN.copy(
              address = token1,
              name = "token1",
              symbol = "token1"
            ),
            LRC_TOKEN.copy(
              address = token2,
              name = "token2",
              symbol = "token2"
            ),
            LRC_TOKEN.copy(
              address = token3,
              name = "token3",
              symbol = "token3"
            )
          )
        ),
        timeout.duration
      )

      val marketRes = Await.result(
        metadataManagerActor ? SaveMarketMetadatas.Req(
          markets = Seq(
            LRC_WETH_MARKET.copy(
              baseTokenSymbol = "token1",
              marketHash = MarketPair(token1, WETH_TOKEN.address).hashString,
              marketPair = Some(MarketPair(token1, WETH_TOKEN.address))
            ),
            LRC_WETH_MARKET.copy(
              baseTokenSymbol = "token2",
              status = MarketMetadata.Status.READONLY,
              marketHash = MarketPair(token2, WETH_TOKEN.address).hashString,
              marketPair = Some(MarketPair(token2, WETH_TOKEN.address))
            ),
            LRC_WETH_MARKET.copy(
              baseTokenSymbol = "token3",
              status = MarketMetadata.Status.TERMINATED,
              marketHash = MarketPair(token3, WETH_TOKEN.address).hashString,
              marketPair = Some(MarketPair(token3, WETH_TOKEN.address))
            )
          )
        ),
        timeout.duration
      )

      Thread.sleep(10000)
      println(marketRes)

      When("submit an order of active market")

      try {
        val submitRes = SubmitOrder
          .Req(
            Some(
              createRawOrder(
                tokenS = token1,
                amountS = "100".zeros(LRC_TOKEN.decimals),
                amountFee = "20".zeros(LRC_TOKEN.decimals)
              )
            )
          )
          .expect(check((res: SubmitOrder.Res) => res.success))
      } catch {
        case e: ErrorException =>
      }

      Then("the result of submit order is true")

      When("submit an order of readonly market")

      try {
        val submitRes = SubmitOrder
          .Req(
            Some(
              createRawOrder(
                tokenS = token2,
                amountS = "100".zeros(LRC_TOKEN.decimals),
                amountFee = "20".zeros(LRC_TOKEN.decimals)
              )
            )
          )
          .expect(check((res: SubmitOrder.Res) => !res.success))
      } catch {
        case e: ErrorException =>
      }

      Then("the result of submit order is false")

      When("submit an order of terminated market")

      try {
        val submitRes = SubmitOrder
          .Req(
            Some(
              createRawOrder(
                tokenS = token3,
                amountS = "100".zeros(LRC_TOKEN.decimals),
                amountFee = "20".zeros(LRC_TOKEN.decimals)
              )
            )
          )
          .expect(check((res: SubmitOrder.Res) => !res.success))
      } catch {
        case e: ErrorException =>
      }

      Then("the result of submit order is false")
    }

  }

}
