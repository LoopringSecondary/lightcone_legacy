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
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

class MatchesSpec_SomeOrders
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
      val account3 = getUniqueAccount()

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
        s"account1: ${account1.getAddress} submit an order of sell 100 and buy 1."
      )

      val order1 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
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

      Then(
        s"account1: ${account1.getAddress} submit an order of sell 80 and buy 1."
      )

      val order2 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "80".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )(account1)

      SubmitOrder
        .Req(
          rawOrder = Some(order2)
        )
        .expect(
          check(
            (res: SubmitOrder.Res) => res.success
          )
        )

      Then(
        s"account1: ${account1.getAddress} submit an order of sell 60 and buy 1."
      )

      val order3 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "60".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )(account1)

      SubmitOrder
        .Req(
          rawOrder = Some(order3)
        )
        .expect(
          check(
            (res: SubmitOrder.Res) => res.success
          )
        )

      Then(
        s"account1: ${account1.getAddress} submit an order of buy 155 and sell 1."
      )

      val order4 = createRawOrder(
        tokenS = dynamicQuoteToken.getAddress(),
        tokenB = dynamicBaseToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountB = "155".zeros(dynamicBaseToken.getDecimals()),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )(account1)

      SubmitOrder
        .Req(
          rawOrder = Some(order4)
        )
        .expect(
          check(
            (res: SubmitOrder.Res) => res.success
          )
        )

      Then(
        s"account1: ${account1.getAddress} submit an order of buy 145 and sell 1."
      )

      val order5 = createRawOrder(
        tokenS = dynamicQuoteToken.getAddress(),
        tokenB = dynamicBaseToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountB = "145".zeros(dynamicBaseToken.getDecimals()),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )(account1)

      SubmitOrder
        .Req(
          rawOrder = Some(order5)
        )
        .expect(
          check(
            (res: SubmitOrder.Res) => res.success
          )
        )

      Then(
        s"orders of ${account1.getAddress} are Pending,order book ,availableBalance are updated"
      )

      defaultValidate(
        getOrdersMatcher = containsInGetOrders(
          STATUS_PENDING,
          order1.hash,
          order2.hash,
          order3.hash,
          order4.hash,
          order5.hash
        ),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = initBalance,
            allowance = initAllowance,
            availableBalance = initBalance - order1.getAmountS - order2.getAmountS - order3.getAmountS - order1.getFeeParams.getAmountFee - order2.getFeeParams.getAmountFee - order3.getFeeParams.getAmountFee,
            availableAllowance = initAllowance - order1.getAmountS - order2.getAmountS - order3.getAmountS - order1.getFeeParams.getAmountFee - order2.getFeeParams.getAmountFee - order3.getFeeParams.getAmountFee
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 240 &&
                res.getOrderbook.buys.map(_.amount.toDouble).sum == 300
          ), defaultMatcher, defaultMatcher)
        )
      )(account1)

      Then(
        s"account2: ${account2.getAddress} submit an order of sell 110 -- buy 1."
      )

      var submitted = false
      addSendRawTxExpects({
        case req: SendRawTransaction.Req => {
          submitted = true
          SendRawTransaction.Res()
        }
      })

      val order6 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "110".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )(account2)

      SubmitOrder
        .Req(
          rawOrder = Some(order6)
        )
        .expect(
          check((res: SubmitOrder.Res) => res.success)
        )

      Then(
        s"account2: ${account2.getAddress} submit an order of sell 90 -- buy 1."
      )

      val order7 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "90".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )(account2)

      SubmitOrder
        .Req(
          rawOrder = Some(order7)
        )
        .expect(
          check((res: SubmitOrder.Res) => res.success)
        )

      Then(
        s"account2: ${account2.getAddress} submit an order of buy 145 and sell 1."
      )

      val order8 = createRawOrder(
        tokenS = dynamicQuoteToken.getAddress(),
        tokenB = dynamicBaseToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountB = "145".zeros(dynamicBaseToken.getDecimals()),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )(account2)

      SubmitOrder
        .Req(
          rawOrder = Some(order8)
        )
        .expect(
          check(
            (res: SubmitOrder.Res) => res.success
          )
        )

      Then(
        s"account2: ${account2.getAddress} submit an order of buy 130 and sell 1."
      )

      val order9 = createRawOrder(
        tokenS = dynamicQuoteToken.getAddress(),
        tokenB = dynamicBaseToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountB = "130".zeros(dynamicBaseToken.getDecimals()),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )(account2)

      SubmitOrder
        .Req(
          rawOrder = Some(order9)
        )
        .expect(
          check(
            (res: SubmitOrder.Res) => res.success
          )
        )

      Then(
        s"orders of ${account2.getAddress} are Pending,order book ,availableBalance are updated"
      )

      defaultValidate(
        getOrdersMatcher = containsInGetOrders(
          STATUS_PENDING,
          order6.hash,
          order7.hash,
          order8.hash,
          order9.hash
        ),
        accountMatcher = accountBalanceMatcher(
          dynamicBaseToken.getAddress(),
          TokenBalance(
            token = dynamicBaseToken.getAddress(),
            balance = initBalance,
            allowance = initAllowance,
            availableBalance = initBalance - order6.getAmountS - order7.getAmountS - order6.getFeeParams.getAmountFee - order7.getFeeParams.getAmountFee,
            availableAllowance = initAllowance - order6.getAmountS - order7.getAmountS - order6.getFeeParams.getAmountFee - order7.getFeeParams.getAmountFee
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) => {
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 440 &&
                res.getOrderbook.buys.map(_.amount.toDouble).sum == 575
            }
          ), defaultMatcher, defaultMatcher)
        )
      )(account2)

      Then(s"${account3.getAddress} submit an order to sell 435 and buy 3")

      val order10 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "435".zeros(dynamicBaseToken.getDecimals()),
        amountB = "3".zeros(dynamicQuoteToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals())
      )(account3)

      SubmitOrder
        .Req(
          rawOrder = Some(order10)
        )
        .expect(
          check(
            (res: SubmitOrder.Res) => res.success
          )
        )

      Then("send raw transaction to submit ring")
      val now = timeProvider.getTimeSeconds()
      while (!submitted && timeProvider
               .getTimeSeconds() - now < timeout.duration.toSeconds) {
        Thread.sleep(500)
      }

      submitted shouldBe true

      Then("dispatch fills and ring mined event")

      addFilledAmountExpects({
        case req: GetFilledAmount.Req =>
          GetFilledAmount.Res(
            filledAmountSMap = (req.orderIds map {
              case order5.hash =>
                order5.hash -> order5.getAmountS
              case order8.hash =>
                order8.hash -> order8.getAmountS
              case order9.hash =>
                order9.hash -> order9.getAmountS
              case order10.hash =>
                order10.hash -> order10.getAmountS
              case id =>
                id -> toAmount(BigInt(0))
            }).toMap
          )
      })

      val txHash =
        "0x19e575bfe3671b54d70fea96aa96e1c4f133e39f07b31c1f2f0fb71e61c4f84a"

      val eventHeader = EventHeader(
        txHash = txHash,
        txStatus = TxStatus.TX_STATUS_SUCCESS
      )

      val fill5 = OrderFilledEvent(
        owner = account1.getAddress,
        orderHash = order5.hash,
        header = Some(eventHeader)
      )

      val fill8 = OrderFilledEvent(
        owner = account2.getAddress,
        orderHash = order8.hash,
        header = Some(eventHeader)
      )

      val fill9 = OrderFilledEvent(
        owner = account2.getAddress,
        orderHash = order9.hash,
        header = Some(eventHeader)
      )

      val fill10 = OrderFilledEvent(
        owner = account3.getAddress,
        orderHash = order10.hash,
        header = Some(eventHeader)
      )

      val ringMinedEvent1 = RingMinedEvent(
        header = Some(eventHeader),
        orderIds = Seq(order5.hash, order10.hash),
        marketPair = Some(dynamicMarketPair)
      )

      val ringMinedEvent2 = RingMinedEvent(
        header = Some(eventHeader),
        orderIds = Seq(order8.hash, order10.hash),
        marketPair = Some(dynamicMarketPair)
      )
      val ringMinedEvent3 = RingMinedEvent(
        header = Some(eventHeader),
        orderIds = Seq(order9.hash, order10.hash),
        marketPair = Some(dynamicMarketPair)
      )

      eventDispatcher.dispatch(fill5)
      eventDispatcher.dispatch(fill8)
      eventDispatcher.dispatch(fill9)
      eventDispatcher.dispatch(fill10)
      eventDispatcher.dispatch(ringMinedEvent1)
      eventDispatcher.dispatch(ringMinedEvent2)
      eventDispatcher.dispatch(ringMinedEvent3)

      val addressBalanceUpdatedEvent1 = AddressBalanceUpdatedEvent(
        address = account1.getAddress,
        token = dynamicBaseToken.getAddress(),
        balance = initBalance + order5.getAmountB - order5.getFeeParams.getAmountFee,
        block = 1L
      )

      val addressBalanceUpdatedEvent2 = AddressBalanceUpdatedEvent(
        address = account2.getAddress,
        token = dynamicBaseToken.getAddress(),
        balance = initBalance + order8.getAmountB - order8.getFeeParams.getAmountFee + order9.getAmountB - order9.getFeeParams.getAmountFee,
        block = 1L
      )

      val addressBalanceUpdatedEvent3 = AddressBalanceUpdatedEvent(
        address = account3.getAddress,
        token = dynamicBaseToken.getAddress(),
        balance = initBalance - order10.getAmountS - order10.getFeeParams.getAmountFee,
        block = 1L
      )

      val addressAllowanceUpdatedEvent3 = AddressAllowanceUpdatedEvent(
        address = account3.getAddress,
        token = dynamicBaseToken.getAddress(),
        allowance = initBalance - order10.getAmountS - order10.getFeeParams.getAmountFee,
        block = 1L
      )

      eventDispatcher.dispatch(addressBalanceUpdatedEvent1)
      eventDispatcher.dispatch(addressBalanceUpdatedEvent2)
      eventDispatcher.dispatch(addressBalanceUpdatedEvent3)
      eventDispatcher.dispatch(addressAllowanceUpdatedEvent3)

      Then(
        "order5, order8, order9, order10 are STATUS_COMPLETELY_FILLED and others are STATUS_PENDING"
      )
      GetOrdersByHash
        .Req(
          hashes = Seq(
            order1.hash,
            order2.hash,
            order3.hash,
            order4.hash,
            order5.hash,
            order6.hash,
            order7.hash,
            order8.hash,
            order9.hash,
            order10.hash
          )
        )
        .expectUntil(
          containsInGetOrdersByHash(
            STATUS_COMPLETELY_FILLED,
            order5.hash,
            order8.hash,
            order9.hash,
            order10.hash
          ) and containsInGetOrdersByHash(
            STATUS_PENDING,
            order1.hash,
            order2.hash,
            order3.hash,
            order4.hash,
            order6.hash
          )
        )

      Then("order book is updated")
      GetOrderbook
        .Req(
          size = 10,
          marketPair = Some(dynamicMarketPair)
        )
        .expect(
          check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 440 &&
                res.getOrderbook.buys.map(_.amount.toDouble).sum == 155
          )
        )

      Then(
        s"${account1.getAddress} , ${account2.getAddress} and ${account3.getAddress} are updated"
      )
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
              balance = initBalance + order5.getAmountB - order5.getFeeParams.getAmountFee,
              allowance = initAllowance,
              availableBalance = initBalance + order5.getAmountB - order5.getFeeParams.getAmountFee - order1.getAmountS - order2.getAmountS - order3.getAmountS - order1.getFeeParams.getAmountFee - order2.getFeeParams.getAmountFee - order3.getFeeParams.getAmountFee,
              availableAllowance = initAllowance - order1.getAmountS - order2.getAmountS - order3.getAmountS - order1.getFeeParams.getAmountFee - order2.getFeeParams.getAmountFee - order3.getFeeParams.getAmountFee
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
              balance = initBalance + order8.getAmountB - order8.getFeeParams.getAmountFee + order9.getAmountB - order9.getFeeParams.getAmountFee,
              allowance = initAllowance,
              availableBalance = initBalance + order8.getAmountB - order8.getFeeParams.getAmountFee + order9.getAmountB - order9.getFeeParams.getAmountFee - order6.getAmountS - order7.getAmountS - order6.getFeeParams.getAmountFee - order7.getFeeParams.getAmountFee,
              availableAllowance = initAllowance - order6.getAmountS - order7.getAmountS - order6.getFeeParams.getAmountFee - order7.getFeeParams.getAmountFee
            )
          )
        )

      GetAccount
        .Req(
          address = account3.getAddress,
          allTokens = true
        )
        .expectUntil(
          accountBalanceMatcher(
            token = dynamicBaseToken.getAddress(),
            tokenBalance = TokenBalance(
              token = dynamicBaseToken.getAddress(),
              balance = initBalance - order10.getAmountS - order10.getFeeParams.getAmountFee,
              allowance = initAllowance - order10.getAmountS - order10.getFeeParams.getAmountFee,
              availableBalance = initBalance - order10.getAmountS - order10.getFeeParams.getAmountFee,
              availableAllowance = initAllowance - order10.getAmountS - order10.getFeeParams.getAmountFee
            )
          )
        )
    }
  }

}
