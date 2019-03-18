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
  STATUS_PENDING,
  STATUS_SOFT_CANCELLED_BY_USER
}
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._

class CancelOrderSpec_byOwnerMarketPair
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("cancel orders of status=STATUS_PENDING") {
    scenario("3: cancel by owner-marketPair") {

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

      Then("submit an order of market:LRC-WETH.")
      val order1 = createRawOrder()
      val submitRes1 = SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))
      info(s"the result of submit order of LRC-WETH is ${submitRes1.success}")

      Then("submit an order of market:GTO-WETH.")
      val order2 =
        createRawOrder(tokenS = GTO_TOKEN.address, tokenFee = GTO_TOKEN.address)
      val submitRes2 = SubmitOrder
        .Req(Some(order2))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then(
        s"cancel the orders of owner:${account.getAddress} and market:${LRC_WETH_MARKET.getMarketPair}."
      )
      val cancelByOwnerReq =
        CancelOrder.Req(
          owner = account.getAddress,
          marketPair = Some(LRC_WETH_MARKET.getMarketPair),
          status = STATUS_SOFT_CANCELLED_BY_USER,
          time = BigInt(timeProvider.getTimeSeconds())
        )
      val sig = generateCancelOrderSig(cancelByOwnerReq)
      val cancelRes = cancelByOwnerReq
        .withSig(sig)
        .expect(check { res: CancelOrder.Res =>
          res.status == cancelByOwnerReq.status
        })

      Then("check the cancel result.")
      val initGTOBalance =
        accountInitRes.getAccountBalance.tokenBalanceMap(GTO_TOKEN.address)
      val expectedAccountBalance = accountInitRes.getAccountBalance.copy(
        tokenBalanceMap = accountInitRes.getAccountBalance.tokenBalanceMap + (GTO_TOKEN.address -> initGTOBalance
          .copy(
            availableBalance = initGTOBalance.balance - order2.amountS - order2.getFeeParams.amountFee,
            availableAlloawnce = initGTOBalance.allowance - order2.amountS - order2.getFeeParams.amountFee
          ))
      )
      val balance: BigInt = initGTOBalance.balance - order2.amountS
      val allowance: BigInt = initGTOBalance.allowance - order2.amountS
      val expectedAccountRes =
        accountInitRes.copy(accountBalance = Some(expectedAccountBalance))
      defaultValidate(
        containsInGetOrders(
          STATUS_SOFT_CANCELLED_BY_USER,
          order1.hash
        ) and containsInGetOrders(
          STATUS_PENDING,
          order2.hash
        ),
        resEqual(expectedAccountRes),
        Map(
//          LRC_WETH_MARKET.getMarketPair -> (orderBookIsEmpty(),
//          userFillsIsEmpty(),
//          marketFillsIsEmpty()),
          GTO_WETH_MARKET.getMarketPair -> (not(orderBookIsEmpty()),
          userFillsIsEmpty(),
          marketFillsIsEmpty())
        )
      )

      Then("cancel another order.")
      val order3 = createRawOrder(validSince = order1.validSince + 100)
      val cancelAnotherReq =
        CancelOrder.Req(
          owner = order3.owner,
          id = order3.hash,
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
