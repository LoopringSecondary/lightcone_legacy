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

import io.lightcone.core.OrderStatus._
import io.lightcone.core.{MarketPair, RawOrder}
import io.lightcone.ethereum._
import io.lightcone.ethereum.event._
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import org.scalatest._
import org.web3j.crypto.{Credentials, Hash}

class EventsSpec_RingMinedEvent
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("test event:RingMinedEvent") {
    scenario("1: too many FAILURES") {
      Given("an account with enough Balance")
      implicit val account = getUniqueAccount()
      val account1 = getUniqueAccount()
      val getAccountReq = GetAccount.Req(
        address = account.getAddress,
        allTokens = true
      )
      val accountInitRes = getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )
      val avaliableAlowanceInit: BigInt = accountInitRes.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableAllowance
      val avaliableBalanceInit: BigInt = accountInitRes.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableBalance
      info(
        s"the inited balance is : {balance: ${avaliableBalanceInit}, allowance:${avaliableAlowanceInit}"
      )

      Given("submit first order.")
      val order = createRawOrder(
        tokenS = dynamicMarketPair.baseToken,
        tokenB = dynamicMarketPair.quoteToken,
        tokenFee = dynamicMarketPair.baseToken
      )
      val submitRes = SubmitOrder
        .Req(Some(order))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Given("submit another order that can be matched with the first order.")
      Then("dispatch a RingMinedEvent.")
      val failureCount = system.settings.config
        .getInt("market_manager.max-ring-failures-per-order")
      And(s"repeated it more than ${failureCount} times.")
      (0 until failureCount).foreach { i =>
        submitAndDispatchEvent(i, order)(account1)
      }

      Then(
        s" the status of the first order should be STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES after more than ${failureCount} times failures "
      )
      val getOrderRes = GetOrders
        .Req(owner = account.getAddress)
        .expectUntil(check((res: GetOrders.Res) => {
          val order1Res =
            res.orders.find(_.hash.equalsIgnoreCase(order.hash))
          order1Res.nonEmpty && order1Res.get.getState.status == STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES
        }))
      val order1Res =
        getOrderRes.orders.find(_.hash.equalsIgnoreCase(order.hash))
      info(
        s"the first order is : ${JsonPrinter.printJsonString(order1Res)}"
      )
      order1Res should not be (empty)
      order1Res.get.getState.status should be(
        STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES
      )
    }
  }

  def submitAndDispatchEvent(
      i: Int,
      firstOrder: RawOrder
    )(
      implicit
      account: Credentials
    ) = {
    val order = createRawOrder(
      tokenS = dynamicMarketPair.quoteToken,
      tokenB = dynamicMarketPair.baseToken,
      tokenFee = dynamicMarketPair.baseToken,
      validUntil = timeProvider.getTimeSeconds().toInt + i * 100
    )
    val ringMinedEvent = RingMinedEvent(
      header = Some(
        EventHeader(
          blockHeader = Some(BlockHeader(height = 110 + i)),
          txHash = Hash.sha3(order.hash),
          txStatus = TxStatus.TX_STATUS_FAILED
        )
      ),
      orderIds = Seq(order.hash, firstOrder.hash),
      marketPair = Some(MarketPair(order.tokenS, order.tokenB))
    )

    addSendRawTxExpects({
      case req: SendRawTransaction.Req => {
        eventDispatcher.dispatch(ringMinedEvent)
        SendRawTransaction.Res()
      }
    })

    SubmitOrder
      .Req(Some(order))
      .expect(check((res: SubmitOrder.Res) => res.success))

    Thread.sleep(1500)
  }
}
