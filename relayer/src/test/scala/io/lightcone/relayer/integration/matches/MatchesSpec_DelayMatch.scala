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

package io.lightcone.relayer.integration.matches

import io.lightcone.core.OrderStatus._
import io.lightcone.ethereum.TxStatus
import io.lightcone.ethereum.event._
import io.lightcone.lib.NumericConversion._
import io.lightcone.relayer.actors.GasPriceActor
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

class MatchesSpec_DelayMatch
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {

  feature("order matches") {
    scenario("complete matches orders") {
      Given("two accounts with enough balance and allowance")
      val account1 = getUniqueAccount()
      val account2 = getUniqueAccount()

      val initAccount = GetAccount
        .Req(
          account1.getAddress,
          tokens = Seq(dynamicBaseToken.getAddress())
        )
        .expectUntil(
          check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
        )

      val initDynamicToken = initAccount.getAccountBalance.tokenBalanceMap(
        dynamicBaseToken.getAddress()
      )
      val initBalance: BigInt = initDynamicToken.getBalance
      val initAllowance: BigInt = initDynamicToken.getAllowance

      Then(
        "set gas price to 100 Gwei and sleep 2s for market manager to update"
      )

      GasPriceActor.broadcast(SetGasPrice.Req("100".zeros(9)))

      Thread.sleep(2000)

      GetGasPrice
        .Req()
        .expectUntil(
          check(
            (res: GetGasPrice.Res) =>
              toBigInt(res.getGasPrice) == "100".zeros(9)
          )
        )

      When(
        s"account1: ${account1.getAddress} submit an order of sell 100 LRC and set fee to 0.001 LRC."
      )

      val order1 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "1".zeros(dynamicBaseToken.getDecimals() - 3)
      )(account1)

      SubmitOrder
        .Req(
          rawOrder = Some(order1)
        )
        .expect(
          check(
            (res: SubmitOrder.Res) => res.success
          )
        )

      And(
        s"account2: ${account2.getAddress} submit an order of buy 100 LRC and set fee to 0.001 LRC."
      )

      var submitted = false
      addSendRawTxExpects({
        case req: SendRawTransaction.Req => {
          submitted = true
          println("submitted order")
          SendRawTransaction.Res()
        }
      })

      val order2 = createRawOrder(
        tokenB = dynamicBaseToken.getAddress(),
        tokenS = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountB = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "1".zeros(dynamicBaseToken.getDecimals() - 3)
      )(account2)

      SubmitOrder
        .Req(
          rawOrder = Some(order2)
        )
        .expect(
          check((res: SubmitOrder.Res) => res.success)
        )

      Then(
        "order1 and order2 are submitted successfully and status are STATUS_PENDING"
      )

      GetOrdersByHash
        .Req(
          hashes = Seq(order1.hash, order2.hash)
        )
        .expect(
          containsInGetOrdersByHash(STATUS_PENDING, order1.hash, order2.hash)
        )

      Then("wait 5 seconds and check not submit the ring")
      Thread.sleep(5000)

      submitted shouldBe false

      Then("set gas price to 1 Gwei ")

      GasPriceActor.broadcast(SetGasPrice.Req("1".zeros(9)))

      GetGasPrice
        .Req()
        .expectUntil(
          check(
            (res: GetGasPrice.Res) => toBigInt(res.getGasPrice) == "1".zeros(9)
          )
        )

      Then("send raw transaction to submit ring")
      val now = timeProvider.getTimeSeconds()
      while (!submitted && timeProvider
               .getTimeSeconds() - now < 10) {
        Thread.sleep(500)
      }

      submitted shouldBe true

      Then("order book is empty")
      GetOrderbook
        .Req(
          size = 10,
          marketPair = Some(dynamicMarketPair)
        )
        .expect(
          orderBookIsEmpty()
        )

      Then("dispatch fills and ring mined event")

      addFilledAmountExpects({
        case req: GetFilledAmount.Req =>
          GetFilledAmount.Res(
            filledAmountSMap = (req.orderIds map { id =>
              if (id == order1.hash)
                id -> order1.getAmountS
              else if (id == order2.hash) id -> order2.getAmountS
              else id -> toAmount(BigInt(0))
            }).toMap
          )
      })

      val txHash =
        "0x19e575bfe3671b54d70fea96aa96e1c4f133e39f07b31c1f2f0fb71e61c4f84a"

      val eventHeader = EventHeader(
        txHash = txHash,
        txStatus = TxStatus.TX_STATUS_SUCCESS
      )

      val fill1 = OrderFilledEvent(
        owner = account1.getAddress,
        orderHash = order1.hash,
        header = Some(eventHeader)
      )

      val fill2 = OrderFilledEvent(
        owner = account2.getAddress,
        orderHash = order2.hash,
        header = Some(eventHeader)
      )

      val ringMinedEvent = RingMinedEvent(
        header = Some(eventHeader),
        orderIds = Seq(order1.hash, order2.hash),
        marketPair = Some(dynamicMarketPair)
      )
      eventDispatcher.dispatch(fill1)
      eventDispatcher.dispatch(fill2)
      eventDispatcher.dispatch(ringMinedEvent)

      val addressBalanceUpdatedEvent1 = AddressBalanceUpdatedEvent(
        address = account1.getAddress,
        token = dynamicBaseToken.getAddress(),
        balance = initBalance - order1.getAmountS - order1.getFeeParams.getAmountFee,
        block = 1L
      )

      val addressAllowanceUpdatedEvent1 = AddressAllowanceUpdatedEvent(
        address = account1.getAddress,
        token = dynamicBaseToken.getAddress(),
        allowance = initAllowance - order1.getAmountS - order1.getFeeParams.getAmountFee,
        block = 1L
      )

      eventDispatcher.dispatch(addressAllowanceUpdatedEvent1)
      eventDispatcher.dispatch(addressBalanceUpdatedEvent1)

      val addressBalanceUpdatedEvent2 = AddressBalanceUpdatedEvent(
        address = account2.getAddress,
        token = dynamicBaseToken.getAddress(),
        balance = initBalance + order2.getAmountB - order2.getFeeParams.getAmountFee,
        block = 1L
      )
      eventDispatcher.dispatch(addressBalanceUpdatedEvent2)

      Then("order1 and order2 are STATUS_COMPLETELY_FILLED")
      GetOrdersByHash
        .Req(
          hashes = Seq(order1.hash, order2.hash)
        )
        .expectUntil(
          containsInGetOrdersByHash(
            STATUS_COMPLETELY_FILLED,
            order1.hash,
            order2.hash
          )
        )

      Then("order book is empty")
      GetOrderbook
        .Req(
          size = 10,
          marketPair = Some(dynamicMarketPair)
        )
        .expect(
          orderBookIsEmpty()
        )

      Then(s"${account1.getAddress} and ${account2.getAddress} are updated")
      GetAccount
        .Req(
          address = account1.getAddress,
          allTokens = true
        )
        .expectUntil(
          accountBalanceMatcher(
            token = dynamicBaseToken.getAddress(),
            tokenBalance = TokenBalance(
              token = dynamicBaseToken.getAddress(),
              balance = initBalance - order1.getAmountS - order1.getFeeParams.getAmountFee,
              allowance = initAllowance - order1.getAmountS - order1.getFeeParams.getAmountFee,
              availableBalance = initBalance - order1.getAmountS - order1.getFeeParams.getAmountFee,
              availableAllowance = initAllowance - order1.getAmountS - order1.getFeeParams.getAmountFee
            )
          )
        )

      GetAccount
        .Req(
          address = account2.getAddress,
          allTokens = true
        )
        .expectUntil(
          accountBalanceMatcher(
            token = dynamicBaseToken.getAddress(),
            tokenBalance = TokenBalance(
              token = dynamicBaseToken.getAddress(),
              balance = initBalance + order2.getAmountB - order2.getFeeParams.getAmountFee,
              allowance = initAllowance,
              availableBalance = initBalance + order2.getAmountB - order2.getFeeParams.getAmountFee,
              availableAllowance = initAllowance
            )
          )
        )

    }
  }

}
