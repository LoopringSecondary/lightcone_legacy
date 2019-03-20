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
import io.lightcone.ethereum.TxStatus.TX_STATUS_SUCCESS
import io.lightcone.ethereum.event._
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers.check
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.relayer.integration._
import org.scalatest._

class SubmitOrderSpec_reSubmitCancelled
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with Matchers {

  feature("submit  order ") {
    scenario("enough balance and enough allowance") {
      implicit val account = getUniqueAccount()
      Given(
        s"an new account with enough balance and enough allowance: ${account.getAddress}"
      )

      When("submit an order.")

      val order = createRawOrder(
        amountS = "40".zeros(LRC_TOKEN.decimals),
        amountFee = "10".zeros(LRC_TOKEN.decimals)
      )
      try {
        SubmitOrder
          .Req(Some(order))
          .expect(check((res: SubmitOrder.Res) => res.success))
      } catch {
        case e: ErrorException =>
      }
      val getOrdersRes = GetOrders
        .Req(owner = account.getAddress)
        .expectUntil(
          check((res: GetOrders.Res) => {
            res.orders.head.getState.status.isStatusPending
          })
        )

      Then(
        s"the status of the order just submitted is ${getOrdersRes.orders.head.getState.status}"
      )

      val cancelEvent = OrdersCancelledOnChainEvent(
        owner = account.getAddress,
        header = Some(EventHeader(txStatus = TX_STATUS_SUCCESS)),
        orderHashes = Seq(order.hash)
      )
      eventDispatcher.dispatch(cancelEvent)
      GetOrders
        .Req(owner = account.getAddress)
        .expectUntil(
          check((res: GetOrders.Res) => {
            res.orders.head.getState.status.isStatusOnchainCancelledByUser
          })
        )
      When("cancel the order by on chain event")

      And("resubmit the order")

      try {
        SubmitOrder
          .Req(Some(order))
          .expect(check((res: SubmitOrder.Res) => !res.success))
      } catch {
        case e: ErrorException =>
      }

      Then("the result of resubmit is failed")
    }
  }

}
