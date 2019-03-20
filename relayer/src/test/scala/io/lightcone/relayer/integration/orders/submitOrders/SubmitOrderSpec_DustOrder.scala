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

import io.lightcone.core._
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers.check
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.relayer.integration._
import org.scalatest._

class SubmitOrderSpec_DustOrder
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with Matchers {

  feature("submit ") {
    scenario("check dust order") {
      implicit val account = getUniqueAccount()
      Given("a new account with enough balance and allowance")

      When("submit an order that fiat value is smaller than dust threshold")

      try {
        val submitRes = SubmitOrder
          .Req(
            Some(
              createRawOrder(
                amountS = "1".zeros(LRC_TOKEN.decimals - 1),
                amountFee = "1".zeros(LRC_TOKEN.decimals - 1),
                amountB = "1".zeros(WETH_TOKEN.decimals - 5)
              )
            )
          )
          .expect(check((res: SubmitOrder.Res) => !res.success))
      } catch {
        case e: ErrorException =>
      }

      GetOrders
        .Req(owner = account.getAddress)
        .expectUntil(
          check((res: GetOrders.Res) => {
            res.orders.head.getState.status.isStatusDustOrder
          })
        )

    }
  }

}
