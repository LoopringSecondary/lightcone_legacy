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
import io.lightcone.core.OrderStatus.{
  STATUS_ONCHAIN_CANCELLED_BY_USER,
  STATUS_SOFT_CANCELLED_BY_USER
}
import io.lightcone.ethereum.{BlockHeader, TxStatus}
import io.lightcone.ethereum.event.{EventHeader, OrdersCancelledOnChainEvent}
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._

class CancelOrderSpec_byCancelEvent
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("cancel orders of status=STATUS_PENDING") {
    scenario("6: cancel order by OrdersCancelledOnChainEvent") {

      Given("an account with enough Balance")
      implicit val account = getUniqueAccount()
      val getAccountReq = GetAccount.Req(
        address = account.getAddress,
        allTokens = true
      )
      val accountInitRes = getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )

      Then("submit an order of market: base-quote.")
      val order1 = createRawOrder(
        tokenS = dynamicMarketPair.baseToken,
        tokenB = dynamicMarketPair.quoteToken
      )
      val submitRes1 = SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))
      info(s"the result of submit order is ${submitRes1.success}")

      Then("submit an order of market:quote-base.")
      val order2 =
        createRawOrder(
          tokenS = dynamicMarketPair.quoteToken,
          tokenB = dynamicMarketPair.baseToken,
          tokenFee = dynamicMarketPair.baseToken,
          validSince = timeProvider.getTimeSeconds().toInt - 1000
        )
      val submitRes2 = SubmitOrder
        .Req(Some(order2))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("dispatch OrdersCancelledOnChainEvent contains this two orders.")
      val evt = OrdersCancelledOnChainEvent(
        header = Some(
          EventHeader(
            blockHeader = Some(BlockHeader(height = 110)),
            txHash = "0x1111111111111",
            txStatus = TxStatus.TX_STATUS_SUCCESS
          )
        ),
        owner = account.getAddress,
        broker = account.getAddress,
        orderHashes = Seq(order1.hash, order2.hash)
      )

      eventDispatcher.dispatch(evt)
      Thread.sleep(1000)
      Then("check the cancel result.")
      defaultValidate(
        containsInGetOrders(
          STATUS_ONCHAIN_CANCELLED_BY_USER,
          order1.hash,
          order2.hash
        ),
        be(accountInitRes),
        Map(
          dynamicMarketPair -> (orderBookIsEmpty(),
          userFillsIsEmpty(),
          marketFillsIsEmpty())
        )
      )

    }
  }
}
