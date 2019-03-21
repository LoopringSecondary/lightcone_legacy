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

import io.lightcone.core.ErrorCode._
import io.lightcone.core.ErrorException
import io.lightcone.core.OrderStatus.STATUS_SOFT_CANCELLED_BY_USER
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._

class CancelOrderSpec_byHash
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("cancel orders of status=STATUS_PENDING") {
    scenario("1: cancel by order_hash") {

      Given("an account with enough Balance")
      implicit val account = getUniqueAccount()
      val getAccountReq = GetAccount.Req(
        address = account.getAddress,
        allTokens = true
      )
      val accountInitRes = getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )

      Then("submit an order.")
      val order = createRawOrder()
      val submitRes = SubmitOrder
        .Req(Some(order))
        .expect(check((res: SubmitOrder.Res) => true))
      info(s"the result of submit order is ${submitRes.success}")

      Then("cancel this order by hash.")
      val cancelReq =
        CancelOrder.Req(
          owner = order.owner,
          id = order.hash,
          status = STATUS_SOFT_CANCELLED_BY_USER,
          time = BigInt(timeProvider.getTimeSeconds())
        )
      val sig = generateCancelOrderSig(cancelReq)
      val cancelRes = cancelReq
        .withSig(sig)
        .expect(check { res: CancelOrder.Res =>
          res.status == cancelReq.status
        })

      Then("check the cancel result.")
      defaultValidate(
        containsInGetOrders(STATUS_SOFT_CANCELLED_BY_USER, order.hash),
        be(accountInitRes),
        Map(
          LRC_WETH_MARKET.getMarketPair -> (orderBookIsEmpty(),
          userFillsIsEmpty(),
          marketFillsIsEmpty())
        )
      )

      Then("cancel another order.")
      val order2 = createRawOrder(validSince = order.validSince + 100)
      val cancelAnotherReq =
        CancelOrder.Req(
          owner = order2.owner,
          id = order2.hash,
          status = STATUS_SOFT_CANCELLED_BY_USER,
          time = BigInt(timeProvider.getTimeSeconds())
        )
      val sig2 = generateCancelOrderSig(cancelAnotherReq)
      val cancelAnotherRes = cancelAnotherReq
        .withSig(sig2)
        .expect(check { res: ErrorException =>
          res.error.code == ERR_ORDER_NOT_EXIST
        })
      cancelAnotherRes.error.code should be(ERR_ORDER_NOT_EXIST)

    }
  }
}
