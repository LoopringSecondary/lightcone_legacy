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
        amountS = "80".zeros(dynamicBaseToken.getDecimals()),
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
            availableAlloawnce = initAllowance - order1.getAmountS - order2.getAmountS - order3.getAmountS - order1.getFeeParams.getAmountFee - order2.getFeeParams.getAmountFee - order3.getFeeParams.getAmountFee
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

      var submitTimes = 0
      addSendRawTxExpects({
        case req: SendRawTransaction.Req => {
          submitTimes += 1
          SendRawTransaction.Res()
        }
      })

      val order6 = createRawOrder(
        tokenB = dynamicBaseToken.getAddress(),
        tokenS = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountB = "110".zeros(dynamicBaseToken.getDecimals()),
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
        tokenB = dynamicBaseToken.getAddress(),
        tokenS = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountB = "90".zeros(dynamicBaseToken.getDecimals()),
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
        s"account1: ${account1.getAddress} submit an order of buy 145 and sell 1."
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
        s"account1: ${account1.getAddress} submit an order of buy 145 and sell 1."
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
            availableAlloawnce = initAllowance - order6.getAmountS - order7.getAmountS - order6.getFeeParams.getAmountFee - order7.getFeeParams.getAmountFee
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


      Then(s"${account3.getAddress} submit ")



//      Then("send raw transaction to submit ring")
//      val now = timeProvider.getTimeSeconds()
//      while (!submitted && timeProvider
//               .getTimeSeconds() - now < timeout.duration.toSeconds) {
//        Thread.sleep(500)
//      }
//
//      submitted shouldBe true
//
//      Then("order book is empty")
//      GetOrderbook
//        .Req(
//          size = 10,
//          marketPair = Some(dynamicMarketPair)
//        )
//        .expect(
//          orderBookIsEmpty()
//        )
//
//      Then("dispatch fills and ring mined event")
//
//      addFilledAmountExpects({
//        case req: GetFilledAmount.Req =>
//          GetFilledAmount.Res(
//            filledAmountSMap = (req.orderIds map { id =>
//              if (id == order1.hash)
//                id -> order1.getAmountS
//              else if (id == order2.hash) id -> order2.getAmountS
//              else id -> toAmount(BigInt(0))
//            }).toMap
//          )
//      })
//
//      val txHash =
//        "0x19e575bfe3671b54d70fea96aa96e1c4f133e39f07b31c1f2f0fb71e61c4f84a"
//
//      val eventHeader = EventHeader(
//        txHash = txHash,
//        txStatus = TxStatus.TX_STATUS_SUCCESS
//      )
//
//      val fill1 = OrderFilledEvent(
//        owner = account1.getAddress,
//        orderHash = order1.hash,
//        header = Some(eventHeader)
//      )
//
//      val fill2 = OrderFilledEvent(
//        owner = account2.getAddress,
//        orderHash = order2.hash,
//        header = Some(eventHeader)
//      )
//
//      val ringMinedEvent = RingMinedEvent(
//        header = Some(eventHeader),
//        orderIds = Seq(order1.hash, order2.hash),
//        marketPair = Some(dynamicMarketPair)
//      )
//      eventDispatcher.dispatch(fill1)
//      eventDispatcher.dispatch(fill2)
//      eventDispatcher.dispatch(ringMinedEvent)
//
//      val addressBalanceUpdatedEvent1 = AddressBalanceUpdatedEvent(
//        address = account1.getAddress,
//        token = dynamicBaseToken.getAddress(),
//        balance = initBalance - order1.getAmountS - order1.getFeeParams.getAmountFee,
//        block = 1L
//      )
//
//      val addressAllowanceUpdatedEvent1 = AddressAllowanceUpdatedEvent(
//        address = account1.getAddress,
//        token = dynamicBaseToken.getAddress(),
//        allowance = initAllowance - order1.getAmountS - order1.getFeeParams.getAmountFee,
//        block = 1L
//      )
//
//      eventDispatcher.dispatch(addressAllowanceUpdatedEvent1)
//      eventDispatcher.dispatch(addressBalanceUpdatedEvent1)
//
//      val addressBalanceUpdatedEvent2 = AddressBalanceUpdatedEvent(
//        address = account2.getAddress,
//        token = dynamicBaseToken.getAddress(),
//        balance = initBalance + order2.getAmountB - order2.getFeeParams.getAmountFee,
//        block = 1L
//      )
//      eventDispatcher.dispatch(addressBalanceUpdatedEvent2)
//
//      Then("order1 and order2 are STATUS_COMPLETELY_FILLED")
//      GetOrdersByHash
//        .Req(
//          hashes = Seq(order1.hash, order2.hash)
//        )
//        .expectUntil(
//          containsInGetOrdersByHash(
//            STATUS_COMPLETELY_FILLED,
//            order1.hash,
//            order2.hash
//          )
//        )
//
//      Then("order book is empty")
//      GetOrderbook
//        .Req(
//          size = 10,
//          marketPair = Some(dynamicMarketPair)
//        )
//        .expect(
//          orderBookIsEmpty()
//        )
//
//      Then(s"${account1.getAddress} and ${account2.getAddress} are updated")
//      GetAccount
//        .Req(
//          address = account1.getAddress,
//          allTokens = true
//        )
//        .expectUntil(
//          accountBalanceMatcher(
//            token = dynamicBaseToken.getAddress(),
//            tokenBalance = TokenBalance(
//              token = dynamicBaseToken.getAddress(),
//              balance = initBalance - order1.getAmountS - order1.getFeeParams.getAmountFee,
//              allowance = initAllowance - order1.getAmountS - order1.getFeeParams.getAmountFee,
//              availableBalance = initBalance - order1.getAmountS - order1.getFeeParams.getAmountFee,
//              availableAlloawnce = initAllowance - order1.getAmountS - order1.getFeeParams.getAmountFee
//            )
//          )
//        )
//
//      GetAccount
//        .Req(
//          address = account2.getAddress,
//          allTokens = true
//        )
//        .expectUntil(
//          accountBalanceMatcher(
//            token = dynamicBaseToken.getAddress(),
//            tokenBalance = TokenBalance(
//              token = dynamicBaseToken.getAddress(),
//              balance = initBalance + order2.getAmountB - order2.getFeeParams.getAmountFee,
//              allowance = initAllowance,
//              availableBalance = initBalance + order2.getAmountB - order2.getFeeParams.getAmountFee,
//              availableAlloawnce = initAllowance
//            )
//          )
//        )
    }
  }

}
