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
import io.lightcone.core.{Amount, ErrorException}
import io.lightcone.core.OrderStatus.STATUS_SOFT_CANCELLED_BY_USER
import io.lightcone.ethereum.{BlockHeader, TxStatus}
import io.lightcone.ethereum.event.{EventHeader, OrderFilledEvent}
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.ethereummock.{
  queryProvider,
  EthereumQueryDataProvider
}
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._

import scala.math.BigInt

class CancelOrderSpec_cancelPartiallyFilledOrder
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
      info(
        s"balance of this account:${account.getAddress} is :${accountInitRes.accountBalance}"
      )

      Then("submit an order.")
      val order = createRawOrder()
      val submitRes = SubmitOrder
        .Req(Some(order))
        .expect(check((res: SubmitOrder.Res) => true))
      info(s"the result of submit order is ${submitRes.success}")

      Then("changed to PARTIALLY_FILLED ")
      val evt = OrderFilledEvent(
        header = Some(
          EventHeader(
            blockHeader = Some(BlockHeader(height = 110)),
            txHash = "0x1111111111111",
            txStatus = TxStatus.TX_STATUS_SUCCESS
          )
        ),
        owner = account.getAddress(),
        orderHash = order.hash
      )

      setMockExpects()

      eventDispatcher.dispatch(evt)
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
    }
  }

  def setMockExpects() = {
    queryProvider = mock[EthereumQueryDataProvider]
    (queryProvider.getFilledAmount _)
      .expects(*)
      .onCall({ req: GetFilledAmount.Req =>
        val amount: Amount = "5".zeros(LRC_TOKEN.decimals)
        GetFilledAmount.Res(
          filledAmountSMap = (req.orderIds map { id =>
            id -> amount
          }).toMap
        )
      })
      .anyNumberOfTimes()

    (queryProvider.getAccount _)
      .expects(*)
      .onCall { req: GetAccount.Req =>
        GetAccount.Res(
          Some(
            AccountBalance(
              address = req.address,
              tokenBalanceMap = req.tokens.map { t =>
                t -> AccountBalance.TokenBalance(
                  token = t,
                  balance = BigInt("1000000000000000000000"),
                  allowance = BigInt("1000000000000000000000"),
                  availableAlloawnce = BigInt("1000000000000000000000"),
                  availableBalance = BigInt("1000000000000000000000")
                )
              }.toMap
            )
          )
        )
      }
      .anyNumberOfTimes()
  }
}
