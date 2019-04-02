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

import akka.util.Timeout
import io.lightcone.core.OrderStatus._
import io.lightcone.core.MarketPair
import io.lightcone.ethereum._
import io.lightcone.ethereum.event._
import io.lightcone.lib.NumericConversion._
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import org.scalatest._
import org.scalatest.matchers._
import org.web3j.crypto.Hash
import spire.math.Rational

import scala.concurrent.Await
import scala.concurrent.duration._

class EventsSpec_OrderFillEvent
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  def accountAllowanceMatcher(expectedAvaliableAllowance: BigInt) = Matcher {
    res: GetAccount.Res =>
      val resAvailabeAllowance: BigInt = res.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableAlloawnce

      MatchResult(
        resAvailabeAllowance == expectedAvaliableAllowance,
        s"AvailabeAllowance not match: ${resAvailabeAllowance} != ${expectedAvaliableAllowance}",
        "AvailabeAllowance match"
      )
  }

  feature("test event:OrderFillEvent") {
    scenario("1: partally filled event") {
      Given("an account with enough Balance")
      implicit val account = getUniqueAccount()
      val getAccountReq = GetAccount.Req(
        address = account.getAddress,
        allTokens = true
      )
      val accountInitRes = getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )
      val avaliableAlowanceInit: BigInt = accountInitRes.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableAlloawnce
      val avaliableBalanceInit: BigInt = accountInitRes.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableBalance
      info(
        s"the inited balance is : {balance: ${avaliableBalanceInit}, allowance:${avaliableAlowanceInit}"
      )

      Given("submit an order.")
      val order = createRawOrder(
        tokenS = dynamicMarketPair.baseToken,
        tokenB = dynamicMarketPair.quoteToken,
        tokenFee = dynamicMarketPair.baseToken
      )
      val submitRes = SubmitOrder
        .Req(Some(order))
        .expect(check((res: SubmitOrder.Res) => res.success))
      val accountRes1 = getAccountReq.expectUntil(
        check(
          (res: GetAccount.Res) =>
            res.accountBalance.nonEmpty && res.getAccountBalance
              .tokenBalanceMap(dynamicMarketPair.baseToken)
              .availableAlloawnce > 0
        )
      )
      val avaliableAlowance1: BigInt = accountRes1.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableAlloawnce
      val avaliableBalance1: BigInt = accountRes1.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableBalance

      info(
        s"the avaliable balance after submit an order is : {balance: ${avaliableBalance1}, allowance:${avaliableAlowance1}"
      )

      Then("dispatcher a partally filled event.")
      val filledAmount: BigInt =
        "5".zeros(dynamicBaseToken.getMetadata.decimals)
      val evt = OrderFilledEvent(
        header = Some(
          EventHeader(
            blockHeader = Some(BlockHeader(height = 110)),
            txHash = Hash.sha3(order.hash),
            txStatus = TxStatus.TX_STATUS_SUCCESS
          )
        ),
        owner = account.getAddress(),
        orderHash = order.hash
      )

      addFilledAmountExpects({
        case req: GetFilledAmount.Req =>
          GetFilledAmount.Res(
            filledAmountSMap = (req.orderIds map { id =>
              id -> toAmount(filledAmount)
            }).toMap
          )
      })
      eventDispatcher.dispatch(evt)
      val usedAmountS = if (order.tokenS != order.getFeeParams.tokenFee) {
        filledAmount
      } else {
        filledAmount +
          (Rational(toBigInt(order.getFeeParams.getAmountFee)) * Rational(
            filledAmount,
            toBigInt(order.getAmountS)
          )).toBigInt
      }

      Then("the result of getBalance should be accountInitRes - usedAmountS.")
      val accountRes2 = getAccountReq.expectUntil(
        accountAllowanceMatcher(avaliableAlowance1 + usedAmountS)
      )
      val avaliableAlowance2: BigInt = accountRes2.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableAlloawnce
      val avaliableBalance2: BigInt = accountRes2.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableBalance

      info(
        s"the avaliable balance after exec partially filled event is : {balance: ${avaliableBalance2}, allowance:${avaliableAlowance2}, ${usedAmountS} "
      )

      And("the status in db should be STATUS_PENDING")
      val getOrderFromDbF = dbModule.orderService.getOrder(order.hash)
      val orderFromDb = Await.result(getOrderFromDbF, timeout.duration)
//      info(s"orderFromDb ${JsonPrinter.printJsonString(orderFromDb)}")
      orderFromDb should not be (None)
      orderFromDb.get.getState.status should be(STATUS_PENDING)

      val getOrderbookReq = GetOrderbook.Req(
        size = 10,
        marketPair = Some(MarketPair(order.tokenS, order.tokenB))
      )

      And("the orderbook should be empty after this order full filled.")
      val orderbook = getOrderbookReq.expectUntil(
        check(
          (res: GetOrderbook.Res) => true
        )
      )
      orderbook.getOrderbook.sells should not be (empty)
      info(s"orderbook ${JsonPrinter.printJsonString(orderbook)}")
    }

    scenario("2: full filled event") {

      Given("an account with enough Balance")
      implicit val account = getUniqueAccount()
      val getAccountReq = GetAccount.Req(
        address = account.getAddress,
        allTokens = true
      )
      val accountInitRes = getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )
      val avaliableAlowanceInit: BigInt = accountInitRes.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableAlloawnce
      val avaliableBalanceInit: BigInt = accountInitRes.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableBalance
      info(
        s"the inited balance is : {balance: ${avaliableBalanceInit}, allowance:${avaliableAlowanceInit}"
      )

      Given("submit two order.")
      val orders = Seq(
        createRawOrder(
          tokenS = dynamicMarketPair.baseToken,
          tokenB = dynamicMarketPair.quoteToken,
          tokenFee = dynamicMarketPair.baseToken
        ),
        createRawOrder(
          tokenS = dynamicMarketPair.baseToken,
          tokenB = dynamicMarketPair.quoteToken,
          tokenFee = dynamicMarketPair.baseToken,
          validUntil = timeProvider.getTimeSeconds().toInt + 10
        )
      )
      orders.map { order =>
        SubmitOrder
          .Req(Some(order))
          .expect(check((res: SubmitOrder.Res) => res.success))
      }

      val ordersAmountS =
        orders.map(order => toBigInt(order.getAmountS)).reduceLeft(_ + _)
      info(s"ordersAmountS ${ordersAmountS}")
//      Thread.sleep(3000)
      val accountRes1 = getAccountReq.expectUntil(
        check(
          (res: GetAccount.Res) =>
            res.accountBalance.nonEmpty && res.getAccountBalance
              .tokenBalanceMap(dynamicMarketPair.baseToken)
              .availableAlloawnce >= ordersAmountS
        )
      )
      val avaliableAlowance1: BigInt = accountRes1.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableAlloawnce
      val avaliableBalance1: BigInt = accountRes1.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableBalance

      info(
        s"the avaliable balance after submit an order is : {balance: ${avaliableBalance1}, allowance:${avaliableAlowance1}"
      )

      Then("dispatcher two full filled event.")
      val events =
        orders.map { order =>
          OrderFilledEvent(
            header = Some(
              EventHeader(
                blockHeader = Some(BlockHeader(height = 110)),
                txHash = Hash.sha3(order.hash),
                txStatus = TxStatus.TX_STATUS_SUCCESS
              )
            ),
            owner = account.getAddress(),
            orderHash = order.hash
          )
        }

      addFilledAmountExpects({
        case req: GetFilledAmount.Req =>
          GetFilledAmount.Res(
            filledAmountSMap = (req.orderIds map { id =>
              if (id.equalsIgnoreCase(orders.head.hash))
                id -> orders.head.getAmountS
              else
                id -> toAmount(
                  (toBigInt(
                    orders.find(_.hash.equalsIgnoreCase(id)).get.getAmountS
                  ) - "1"
                    .zeros(dynamicBaseToken.getMetadata.decimals - 4))
                )
            }).toMap
          )
      })
      events.map(eventDispatcher.dispatch)

      Then("the result of getBalance should be accountInitRes.")
//      Thread.sleep(3000)
      val accountRes2 = getAccountReq.expectUntil(
        accountAllowanceMatcher(avaliableAlowanceInit),
        Timeout(10 second)
      )
      val avaliableAlowance2: BigInt = accountRes2.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableAlloawnce
      val avaliableBalance2: BigInt = accountRes2.getAccountBalance
        .tokenBalanceMap(dynamicMarketPair.baseToken)
        .availableBalance

      info(
        s"the avaliable balance after exec full filled event is : {balance: ${avaliableBalance2}, allowance:${avaliableAlowance2}"
      )

      And("the status in db should be STATUS_COMPLETELY_FILLED")

      val getOrderFromDbF = dbModule.orderService.getOrders(orders.map(_.hash))
      val ordersFromDb = Await.result(getOrderFromDbF, timeout.duration)
//      info(s"orderFromDb ${JsonPrinter.printJsonString(orderFromDb)}")
      ordersFromDb.size should be(orders.size)
      all(ordersFromDb.map(_.getState.status)) should be(
        STATUS_COMPLETELY_FILLED
      )

      val getOrderbookReq = GetOrderbook.Req(
        size = 10,
        marketPair = Some(MarketPair(orders.head.tokenS, orders.head.tokenB))
      )
      And("the orderbook should be empty after this order full filled.")
      val orderbook = getOrderbookReq.expectUntil(
        check(
          (res: GetOrderbook.Res) => true
        )
      )
      info(s"orderbook ${JsonPrinter.printJsonString(orderbook)}")
      orderbook.getOrderbook.sells should be(empty)
      orderbook.getOrderbook.buys should be(empty)
    }
  }
}
