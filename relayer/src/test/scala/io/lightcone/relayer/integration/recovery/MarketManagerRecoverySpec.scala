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

package io.lightcone.relayer.integration.recovery

import akka.actor.PoisonPill
import io.lightcone.core.OrderStatus._
import io.lightcone.core._
import io.lightcone.ethereum.TxStatus
import io.lightcone.ethereum.event._
import io.lightcone.lib.NumericConversion.toAmount
import io.lightcone.relayer.actors._
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._

class MarketManagerRecoverySpec
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with RecoveryHelper
    with ValidateHelper
    with CancelHelper
    with Matchers {

  feature("test recovery") {
    scenario("market manager recovery") {
      implicit val account1 = getUniqueAccount()
      implicit val account2 = getUniqueAccount()

      addAccountExpects({
        case req =>
          GetAccount.Res(
            Some(
              AccountBalance(
                address = req.address,
                tokenBalanceMap = req.tokens.map { t =>
                  t -> AccountBalance.TokenBalance(
                    token = t,
                    balance = "1000".zeros(18),
                    allowance = "1000".zeros(18)
                  )
                }.toMap
              )
            )
          )
      })

      Given("two accounts with enough balance and allowance")

      When("send an request to make specific MultiAccountManager start")
      GetAccount
        .Req(
          address = account1.getAddress,
          allTokens = true
        )
        .expectUntil(
          check(
            (res: GetAccount.Res) => res.accountBalance.nonEmpty
          )
        )

      And(s"${account1.getAddress} submit an order: sell 100")

      val order1 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "100".zeros(dynamicBaseToken.getDecimals()),
        validUntil = (timeProvider.getTimeMillis / 1000).toInt + 15
      )(account1)
      SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s"${account1.getAddress}  submit an order: sell 80")

      val order2 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "80".zeros(dynamicBaseToken.getDecimals())
      )(account1)

      SubmitOrder
        .Req(Some(order2))
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s" ${account1.getAddress} submit an order: sell 60")

      val order3 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "60".zeros(dynamicBaseToken.getDecimals())
      )(account1)

      SubmitOrder
        .Req(Some(order3))
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s" ${account1.getAddress} submit an order: buy 150  ")
      val order4 = createRawOrder(
        tokenB = dynamicBaseToken.getAddress(),
        tokenS = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountB = "150".zeros(dynamicBaseToken.getDecimals())
      )(account1)
      SubmitOrder
        .Req(Some(order4))
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s" ${account1.getAddress} submit an order: buy 155  ")

      val order5 = createRawOrder(
        tokenB = dynamicBaseToken.getAddress(),
        tokenS = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountB = "155".zeros(dynamicBaseToken.getDecimals())
      )(account1)

      SubmitOrder
        .Req(Some(order5))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("total amount for sell is 240 and total buy amount is 305")

      And(
        s"the status of all ${account1.getAddress} submitted orders is pending"
      )

      And(s" ${account1.getAddress} available balance and allowance is 751")

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
            balance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            allowance = "1000".zeros(dynamicBaseToken.getMetadata.decimals),
            availableBalance =
              "751".zeros(dynamicBaseToken.getMetadata.decimals),
            availableAllowance =
              "751".zeros(dynamicBaseToken.getMetadata.decimals)
          )
        ) and accountBalanceMatcher(
          dynamicQuoteToken.getAddress(),
          TokenBalance(
            token = dynamicQuoteToken.getAddress(),
            balance = "1000".zeros(dynamicQuoteToken.getMetadata.decimals),
            allowance = "1000".zeros(dynamicQuoteToken.getMetadata.decimals),
            availableBalance =
              "998".zeros(dynamicQuoteToken.getMetadata.decimals),
            availableAllowance =
              "998".zeros(dynamicQuoteToken.getMetadata.decimals)
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 240 &&
                res.getOrderbook.buys.map(_.amount.toDouble).sum == 305
          ), defaultMatcher, defaultMatcher)
        )
      )(account1)

      When(s"${account2.getAddress} submit an order :sell 110")

      val order6 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "110".zeros(dynamicBaseToken.getDecimals())
      )(account2)
      SubmitOrder
        .Req(Some(order6))
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s"${account2.getAddress} submit an order :sell 90")

      val order7 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "90".zeros(dynamicBaseToken.getDecimals())
      )(account2)

      SubmitOrder
        .Req(Some(order7))
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s" ${account2.getAddress} submit an order: buy 145 ")

      val order8 = createRawOrder(
        tokenB = dynamicBaseToken.getAddress(),
        tokenS = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountB = "145".zeros(dynamicBaseToken.getDecimals())
      )(account2)

      SubmitOrder
        .Req(Some(order8))
        .expect(check((res: SubmitOrder.Res) => res.success))

      And(s" ${account2.getAddress} submit an order: buy 130 ")

      val order9 = createRawOrder(
        tokenB = dynamicBaseToken.getAddress(),
        tokenS = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "1".zeros(dynamicQuoteToken.getDecimals()),
        amountB = "130".zeros(dynamicBaseToken.getDecimals())
      )(account2)
      SubmitOrder
        .Req(Some(order9))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("total amount for sell is 440 and total buy amount is 580")

      And(
        s"the status of all ${account2.getAddress} submitted orders is pending"
      )

      And(s" ${account2.getAddress} available balance and allowance is 794")

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
            balance = "1000".zeros(dynamicBaseToken.getDecimals()),
            allowance = "1000".zeros(dynamicBaseToken.getDecimals()),
            availableBalance = "794".zeros(dynamicBaseToken.getDecimals()),
            availableAllowance = "794".zeros(dynamicBaseToken.getDecimals())
          )
        ) and accountBalanceMatcher(
          dynamicQuoteToken.getAddress(),
          TokenBalance(
            token = dynamicQuoteToken.getAddress(),
            balance = "1000".zeros(dynamicQuoteToken.getDecimals()),
            allowance = "1000".zeros(dynamicQuoteToken.getDecimals()),
            availableBalance = "998".zeros(dynamicQuoteToken.getDecimals()),
            availableAllowance = "998".zeros(dynamicQuoteToken.getDecimals())
          )
        ),
        marketMatchers = Map(
          dynamicMarketPair -> (check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells.map(_.amount.toDouble).sum == 440 &&
                res.getOrderbook.buys.map(_.amount.toDouble).sum == 580
          ), defaultMatcher, defaultMatcher)
        )
      )(account2)

      Then(s"cancel the first order of ${account2.getAddress}")

      val cancelReq =
        CancelOrder.Req(
          owner = order6.owner,
          id = order6.hash,
          status = STATUS_SOFT_CANCELLED_BY_USER,
          time = BigInt(timeProvider.getTimeSeconds())
        )
      val sig = generateCancelOrderSig(cancelReq)(account2)
      cancelReq
        .withSig(sig)
        .expect(check { res: CancelOrder.Res =>
          res.status == cancelReq.status
        })

      val amountB: BigInt = order2.getAmountB
      val amountS: BigInt = order2.getAmountS
      val amountFee: BigInt = order2.getFeeParams.getAmountFee

      Then("send orderFill to mock order fill")

      addFilledAmountExpects({
        case req: GetFilledAmount.Req =>
          GetFilledAmount.Res(
            filledAmountSMap = (req.orderIds map { id =>
              if (id == order2.hash)
                id -> toAmount(amountS / 2)
              else id -> toAmount(BigInt(0))
            }).toMap
          )
      })

      val eventHeader = EventHeader(
        txStatus = TxStatus.TX_STATUS_SUCCESS
      )
      val filled = OrderFilledEvent(
        header = Some(eventHeader),
        owner = account1.getAddress,
        orderHash = order2.hash
      )
      eventDispatcher.dispatch(filled)

      val addressBalanceUpdatedEvent = AddressBalanceUpdatedEvent(
        address = account1.getAddress,
        token = dynamicBaseToken.getAddress(),
        balance = "1000"
          .zeros(dynamicBaseToken.getDecimals()) - amountS / 2 - amountFee / 2,
        block = 1L
      )

      eventDispatcher.dispatch(addressBalanceUpdatedEvent)

      val addressAllowanceUpdatedEvent = AddressAllowanceUpdatedEvent(
        address = account1.getAddress,
        token = dynamicBaseToken.getAddress(),
        allowance = "1000"
          .zeros(dynamicBaseToken.getDecimals()) - amountS / 2 - amountFee / 2,
        block = 1L
      )
      eventDispatcher.dispatch(addressAllowanceUpdatedEvent)

      Then("sleep 10 seconds to wait order1 expire")

      Thread.sleep(10000)

      When(
        "send PoisonPill to kill specific  marketManagerActor"
      )

      getMarketManagerShardActor(dynamicMarketPair) ! PoisonPill

      Then("send a request to make specific market manager actor restart")

      actorRefs.get(MarketManagerActor.name) ! Notify(
        KeepAliveActor.NOTIFY_MSG,
        s"${dynamicBaseToken.getAddress()}-${dynamicQuoteToken.getAddress()}"
      )

      Then("sleep 10 seconds to wait recover completion")

      Thread.sleep(10000)

      Then("the order book is recovered")
      GetOrderbook
        .Req(
          size = 20,
          marketPair = Some(dynamicMarketPair)
        )
        .expectUntil(
          check(
            (res: GetOrderbook.Res) =>
              res.getOrderbook.sells
                .map(_.amount.toDouble)
                .sum == (amountS / 2 + order3.getAmountS + order7.getAmountS) / "1"
                .zeros(dynamicBaseToken.getDecimals()) &&
                res.getOrderbook.buys
                  .map(_.amount.toDouble)
                  .sum == (amount2BigInt(
                  order4.getAmountB
                ) + order5.getAmountB + order8.getAmountB + order9.getAmountB) / "1"
                  .zeros(dynamicBaseToken.getDecimals())
          )
        )
      And("orders are recovered")
      GetOrders
        .Req(
          owner = account1.getAddress
        )
        .expectUntil(
          containsInGetOrders(
            STATUS_EXPIRED,
            order1.hash
          ) and containsInGetOrders(
            STATUS_PENDING,
            order2.hash,
            order3.hash,
            order4.hash,
            order5.hash
          )
        )

      GetOrders
        .Req(
          owner = account2.getAddress
        )
        .expectUntil(
          containsInGetOrders(
            STATUS_PENDING,
            order7.hash,
            order8.hash,
            order9.hash
          ) and containsInGetOrders(
            STATUS_SOFT_CANCELLED_BY_USER,
            order6.hash
          )
        )
      And(s"${account1.getAddress} is recovered")

      GetAccount
        .Req(
          address = account1.getAddress,
          allTokens = true
        )
        .expectUntil(
          accountBalanceMatcher(
            dynamicBaseToken.getAddress(),
            TokenBalance(
              token = dynamicBaseToken.getAddress(),
              balance = "1000"
                .zeros(dynamicBaseToken.getDecimals()) - amountS / 2 - amountFee / 2,
              allowance = "1000"
                .zeros(dynamicBaseToken.getDecimals()) - amountS / 2 - amountFee / 2,
              availableBalance =
                "1000"
                  .zeros(dynamicBaseToken.getDecimals()) - order2.getAmountS - order3.getAmountS - order2.getFeeParams.getAmountFee - order3.getFeeParams.getAmountFee,
              availableAllowance =
                "1000"
                  .zeros(dynamicBaseToken.getDecimals()) - order2.getAmountS - order3.getAmountS - order2.getFeeParams.getAmountFee - order3.getFeeParams.getAmountFee
            )
          )
        )
      And(s"${account2.getAddress} is recovered")
      GetAccount
        .Req(
          address = account2.getAddress,
          allTokens = true
        )
        .expectUntil(
          accountBalanceMatcher(
            dynamicBaseToken.getAddress(),
            TokenBalance(
              token = dynamicBaseToken.getAddress(),
              balance = "1000".zeros(dynamicBaseToken.getDecimals()),
              allowance = "1000".zeros(dynamicBaseToken.getDecimals()),
              availableBalance =
                "1000"
                  .zeros(dynamicBaseToken.getDecimals()) - order7.getAmountS - order7.getFeeParams.getAmountFee,
              availableAllowance =
                "1000"
                  .zeros(dynamicBaseToken.getDecimals()) - order7.getAmountS - order7.getFeeParams.getAmountFee
            )
          )
        )
    }
  }
}
