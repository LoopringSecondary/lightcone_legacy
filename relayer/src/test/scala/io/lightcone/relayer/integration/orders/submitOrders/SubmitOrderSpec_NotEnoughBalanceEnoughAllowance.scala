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

package io.lightcone.relayer.integration.orders.submitOrders

import io.lightcone.core._
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.data._
import io.lightcone.relayer.ethereummock._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers.check
import io.lightcone.relayer.integration._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._
import io.lightcone.relayer._

import scala.concurrent.Await
import scala.math.BigInt

class SubmitOrderSpec_NotEnoughBalanceEnoughAllowance
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with Matchers {

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    queryProvider = mock[EthereumQueryDataProvider]
    accessProvider = mock[EthereumAccessDataProvider]
    //账户余额
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
                  balance = "30".zeros(LRC_TOKEN.decimals),
                  allowance = "100000".zeros(LRC_TOKEN.decimals)
                )
              }.toMap
            )
          )
        )
      }
      .anyNumberOfTimes()

    //burnRate
    (queryProvider.getBurnRate _)
      .expects(*)
      .onCall({ req: GetBurnRate.Req =>
        GetBurnRate.Res(burnRate = Some(BurnRate()))
      })
      .anyNumberOfTimes()

    //batchGetCutoffs
    (queryProvider.batchGetCutoffs _)
      .expects(*)
      .onCall({ req: BatchGetCutoffs.Req =>
        BatchGetCutoffs.Res(
          req.reqs.map { r =>
            GetCutoff.Res(
              r.broker,
              r.owner,
              r.marketHash,
              BigInt(0)
            )
          }
        )
      })
      .anyNumberOfTimes()

    //orderCancellation
    (queryProvider.getOrderCancellation _)
      .expects(*)
      .onCall({ req: GetOrderCancellation.Req =>
        GetOrderCancellation.Res(
          cancelled = false,
          block = 100
        )
      })
      .anyNumberOfTimes()

    //getFilledAmount
    (queryProvider.getFilledAmount _)
      .expects(*)
      .onCall({ req: GetFilledAmount.Req =>
        val zeroAmount: Amount = BigInt(0)
        GetFilledAmount.Res(
          filledAmountSMap = (req.orderIds map { id =>
            id -> zeroAmount
          }).toMap
        )
      })
      .anyNumberOfTimes()
  }

  feature("submit an order") {
    scenario("enough balance and not enough allowance") {
      implicit val account = getUniqueAccount()
      Given(
        s"an new account with enough balance and not enough allowance: ${account.getAddress}"
      )

      val getBalanceReq = GetAccount.Req(
        account.getAddress,
        tokens = Seq(GTO_TOKEN.name)
      )
      val res = getBalanceReq.expectUntil(
        check((res: GetAccount.Res) => {
          val lrc_ba = res.getAccountBalance.tokenBalanceMap(GTO_TOKEN.address)
          NumericConversion.toBigInt(lrc_ba.getAllowance) == "100000".zeros(
            GTO_TOKEN.decimals
          ) &&
          NumericConversion.toBigInt(lrc_ba.getAvailableAlloawnce) == "100000"
            .zeros(GTO_TOKEN.decimals) &&
          NumericConversion.toBigInt(lrc_ba.getBalance) == "30".zeros(
            GTO_TOKEN.decimals
          ) &&
          NumericConversion.toBigInt(lrc_ba.getAvailableBalance) == "30"
            .zeros(GTO_TOKEN.decimals)
        })
      )

      When("submit an order.")

      val order = createRawOrder(
        tokenS = GTO_TOKEN.address,
        amountS = "50".zeros(GTO_TOKEN.decimals)
      )
      try {
        val submitRes = SubmitOrder
          .Req(Some(order))
          .expect(check((res: SubmitOrder.Res) => res.success))
      } catch {
        case e: ErrorException =>
      }
      val getOrdersRes = GetOrders
        .Req(owner = account.getAddress)
        .expect(
          check((res: GetOrders.Res) => {
            res.total > 0
          })
        )

      val reOrder = getOrdersRes.orders.head

      Then(
        s"the error of the order just submitted is ${reOrder.getState.status}"
      )

      And(
        s"the actual amountS is ${NumericConversion.toBigInt(reOrder.getState.getActualAmountS)} "
      )

      And(
        s"the actual amountB is ${NumericConversion.toBigInt(reOrder.getState.getActualAmountB)} "
      )

      And(
        s"the actual amountFee is ${NumericConversion.toBigInt(reOrder.getState.getActualAmountFee)} "
      )

      And(
        s"the outstanding amountS is ${NumericConversion.toBigInt(reOrder.getState.getOutstandingAmountS)} "
      )

      And(
        s"the outstanding amountB is ${NumericConversion.toBigInt(reOrder.getState.getOutstandingAmountB)} "
      )

      And(
        s"the outstanding amountB is ${NumericConversion.toBigInt(reOrder.getState.getOutstandingAmountFee)} "
      )

      getBalanceReq.expect(
        check(
          (res: GetAccount.Res) => {
            val lrc_ba =
              res.getAccountBalance.tokenBalanceMap(GTO_TOKEN.address)
            NumericConversion.toBigInt(lrc_ba.getAvailableBalance) == 0 &&
            NumericConversion
              .toBigInt(lrc_ba.getAvailableAlloawnce) == NumericConversion
              .toBigInt(lrc_ba.getAllowance) - "30".zeros(GTO_TOKEN.decimals)
          }
        )
      )

      val orderbookres = GetOrderbook
        .Req(
          size = 10,
          marketPair = Some(
            MarketPair(
              baseToken = GTO_TOKEN.address,
              quoteToken = WETH_TOKEN.address
            )
          )
        )
        .expect(
          check(
            (res: GetOrderbook.Res) => res.getOrderbook.sells.nonEmpty
          )
        )

      val sell = orderbookres.getOrderbook.sells.head

      Then(
        s" data of order book sell is ${sell.price}--${sell.amount}--${sell.total} "
      )

      Then("clear data to avoid other tests being affected")

      //     val cancelOrderReq  =  CancelOrder.Req(id =order.hash,owner = account.getAddress,time = NumericConversion.toAmount(BigInt(timeProvider.getTimeSeconds())))
      //
      //      cancelOrderReq.withSig()

    }

  }

}
