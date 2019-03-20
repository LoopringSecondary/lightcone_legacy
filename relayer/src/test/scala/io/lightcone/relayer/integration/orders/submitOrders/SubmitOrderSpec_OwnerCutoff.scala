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
import io.lightcone.relayer.data._
import io.lightcone.relayer.ethereummock._
import io.lightcone.relayer.integration.AddedMatchers.check
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.relayer.integration._
import io.lightcone.relayer.getUniqueAccount
import org.scalatest._

import scala.math.BigInt

class SubmitOrderSpec_OwnerCutoff
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
                  balance = "1000".zeros(18),
                  allowance = "1000".zeros(18)
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
              BigInt(timeProvider.getTimeSeconds())
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

  feature("submit  order ") {
    scenario("owner cutoff is not zero") {
      implicit val account = getUniqueAccount()
      Given(
        s"an new account with enough balance and enough allowance: ${account.getAddress}"
      )
      And("submit the an order that valid since is smaller than cutoff")
      try {
        SubmitOrder
          .Req(
            Some(
              createRawOrder(
                amountS = "40".zeros(LRC_TOKEN.decimals),
                amountFee = "10".zeros(LRC_TOKEN.decimals),
                validSince = (timeProvider.getTimeSeconds() - 1).toInt
              )
            )
          )
          .expect(check((res: SubmitOrder.Res) => !res.success))
      } catch {
        case e: ErrorException =>
      }

      Then(
        "the result of submit order that valid since is smaller than cutoff is false"
      )

      And("submit the an order that valid since is bigger than cutoff")
      try {
        SubmitOrder
          .Req(
            Some(
              createRawOrder(
                validSince = (timeProvider.getTimeSeconds() + 1).toInt
              )
            )
          )
          .expect(check((res: SubmitOrder.Res) => res.success))
      } catch {
        case e: ErrorException =>
      }

      Then(
        "the result of submit order that valid since is bigger than cutoff is true"
      )

    }
  }

}
