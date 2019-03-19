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
import io.lightcone.core.OrderStatus._
import io.lightcone.ethereum.{BlockHeader, TxStatus}
import io.lightcone.ethereum.event.{CutoffEvent, EventHeader}
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._

class CancelOrderSpec_byCutoffEventOwner
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("cancel orders of status=STATUS_PENDING") {
    scenario("4: cancel orders of owner by CutoffEvent") {

      Given("an account with enough Balance")
      implicit val account = getUniqueAccount()
      val getAccountReq =
        GetAccount.Req(address = account.getAddress, allTokens = true)
      val accountInitRes = getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )
      info(
        s"balance of this account:${account.getAddress} is :${accountInitRes.accountBalance}"
      )

      val lrcTokenBalance =
        accountInitRes.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address)
      val gtoTokenBalance =
        accountInitRes.getAccountBalance.tokenBalanceMap(GTO_TOKEN.address)

      Then(
        "submit two orders  that validSince=now-100 and validSince=now-10 of market:LRC-WETH."
      )
      val order1 =
        createRawOrder(validSince = timeProvider.getTimeSeconds().toInt - 1000)
      val submitRes1 = SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))
      info(
        s"the result of submit first order of LRC-WETH is ${submitRes1.success}"
      )
      val order3 = createRawOrder()
      val submitRes3 = SubmitOrder
        .Req(Some(order3))
        .expect(check((res: SubmitOrder.Res) => res.success))
      info(
        s"the result of submit second order of LRC-WETH is ${submitRes3.success}, ${order3.validSince}"
      )
      Then("submit an order that validSince=now-100 of market:GTO-WETH.")
      val order2 =
        createRawOrder(
          tokenS = GTO_TOKEN.address,
          tokenFee = GTO_TOKEN.address,
          validSince = timeProvider.getTimeSeconds().toInt - 1000
        )
      val submitRes2 = SubmitOrder
        .Req(Some(order2))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then(
        s"dispatch CutoffEvent that owner:${account.getAddress} and market:${LRC_WETH_MARKET.getMarketPair}."
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
        cutoff = timeProvider.getTimeSeconds().toInt - 500
      )

      eventDispatcher.dispatch(cutoff)

      Thread.sleep(1000)
      Then("check the cancel result.")
      val lrcExpectedBalance = lrcTokenBalance.copy(
        availableBalance = lrcTokenBalance.availableBalance - order3.amountS - order3.getFeeParams.amountFee,
        availableAlloawnce = lrcTokenBalance.availableAlloawnce - order3.amountS - order3.getFeeParams.amountFee
      )
      defaultValidate(
        containsInGetOrders(
          STATUS_ONCHAIN_CANCELLED_BY_USER,
          order1.hash,
          order2.hash
        ) and containsInGetOrders(
          STATUS_PENDING,
          order3.hash
        ),
        accountBalanceMatcher(LRC_TOKEN.address, lrcExpectedBalance)
          and accountBalanceMatcher(GTO_TOKEN.address, gtoTokenBalance),
        Map(
          LRC_WETH_MARKET.getMarketPair -> (orderBookIsEmpty(),
          userFillsIsEmpty(),
          marketFillsIsEmpty()),
          GTO_WETH_MARKET.getMarketPair -> (orderBookIsEmpty(),
          userFillsIsEmpty(),
          marketFillsIsEmpty())
        )
      )

      Then(
        "submit two new orders, that validSince=now-1000 and valideSince=now"
      )

      Then("cancel this market again.")
      val cancelAnotherReq =
        CancelOrder.Req(
          owner = account.getAddress,
          marketPair = Some(LRC_WETH_MARKET.getMarketPair),
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
