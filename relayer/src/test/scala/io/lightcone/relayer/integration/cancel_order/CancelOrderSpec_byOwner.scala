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

import scala.math.BigInt

class CancelOrderSpec_byOwner
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("cancel orders of status=STATUS_PENDING") {
    scenario("2: cancel by owner") {

      Given("an account with enough Balance")
      addAccountExpects({
        case req: GetAccount.Req =>
          GetAccount.Res(
            Some(
              AccountBalance(
                address = req.address,
                tokenBalanceMap = req.tokens.map { t =>
                  t -> AccountBalance.TokenBalance(
                    token = t,
                    balance = BigInt("3000000000000000000000"),
                    allowance = BigInt("3000000000000000000000"),
                    availableAlloawnce = BigInt("1000000000000000000000"),
                    availableBalance = BigInt("1000000000000000000000")
                  )
                }.toMap
              )
            )
          )
      })

      implicit val account = getUniqueAccount()
      val getAccountReq =
        GetAccount.Req(address = account.getAddress, allTokens = true)
      val accountInitRes = getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )
      val balance: BigInt = accountInitRes.accountBalance.get
        .tokenBalanceMap(LRC_TOKEN.address)
        .balance

      info(
        s"balance of this account:${account.getAddress} is :${JsonPrinter
          .printJsonString(accountInitRes.accountBalance)}, ${balance}"
      )

      Then("submit an order of market:LRC-WETH.")
      val order1 = createRawOrder()
      val submitRes1 = SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => true))
      info(s"the result of submit order of LRC-WETH is ${submitRes1.success}")

      Then("submit an order of market:GTO-WETH.")
      val order2 = createRawOrder(tokenS = GTO_TOKEN.address)
      val submitRes2 = SubmitOrder
        .Req(Some(order2))
        .expect(check((res: SubmitOrder.Res) => true))
      info(s"the result of submit order of GTO-WETH is ${submitRes2.success}")

      Then(s"cancel the orders of owner:${account.getAddress}.")
      val cancelByOwnerReq =
        CancelOrder.Req(
          owner = account.getAddress,
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
      defaultValidate(
        containsInGetOrders(
          STATUS_SOFT_CANCELLED_BY_USER,
          order1.hash,
          order2.hash
        ),
        be(accountInitRes),
        Map(
          LRC_WETH_MARKET.getMarketPair -> (orderBookIsEmpty(),
          userFillsIsEmpty(),
          marketFillsIsEmpty()),
          GTO_WETH_MARKET.getMarketPair -> (orderBookIsEmpty(),
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
