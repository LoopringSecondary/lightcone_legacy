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

import io.lightcone.core._
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers.check
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.relayer.integration._
import org.scalatest._

import scala.math.BigInt

class SubmitOrderSpec_OwnerCutoffTradingPair
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with Matchers {

  feature("submit  order ") {
    scenario("lrc-weth market cutoff is not zero and others' are zero") {
      implicit val account = getUniqueAccount()
      Given(
        s"an new account with enough balance and enough allowance: ${account.getAddress}"
      )

      addCutoffsExpects({
        case req =>
          BatchGetCutoffs.Res(
            req.reqs.map { r =>
              if (r.marketHash == MarketPair(
                    LRC_TOKEN.address,
                    WETH_TOKEN.address
                  ).hashString)
                GetCutoff.Res(
                  r.broker,
                  r.owner,
                  r.marketHash,
                  BigInt(timeProvider.getTimeSeconds())
                )
              else
                GetCutoff.Res(r.broker, r.owner, r.marketHash, BigInt(0))
            }
          )
      })

      And("submit the an order that valid since is smaller than cutoff")
      try {
        SubmitOrder
          .Req(
            Some(
              createRawOrder(
                amountS = "40".zeros(LRC_TOKEN.decimals),
                amountFee = "10".zeros(LRC_TOKEN.decimals),
                validSince = (timeProvider.getTimeSeconds() - 1).toInt
              )
            )
          )
          .expect(check((res: SubmitOrder.Res) => !res.success))
      } catch {
        case e: ErrorException =>
      }

      Then(
        "the result of submit order that valid since is smaller than cutoff is false"
      )

      And("submit the an order that valid since is bigger than cutoff")
      try {
        SubmitOrder
          .Req(
            Some(
              createRawOrder(
                validSince = (timeProvider.getTimeSeconds() + 1).toInt
              )
            )
          )
          .expect(check((res: SubmitOrder.Res) => res.success))
      } catch {
        case e: ErrorException =>
      }

      Then(
        "the result of submit order that valid since is bigger than cutoff is true"
      )

      When("submit an order of another market")

      try {
        SubmitOrder
          .Req(
            Some(
              createRawOrder(
                tokenS = GTO_TOKEN.address
              )
            )
          )
          .expect(check((res: SubmitOrder.Res) => res.success))
      } catch {
        case e: ErrorException =>
      }
    }
  }

}
