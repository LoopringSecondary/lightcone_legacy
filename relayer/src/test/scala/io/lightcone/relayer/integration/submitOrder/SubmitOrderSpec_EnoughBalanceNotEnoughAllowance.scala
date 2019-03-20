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

package io.lightcone.relayer.integration.submitOrder

import io.lightcone.core._
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers.check
import io.lightcone.relayer.integration.Metadatas.LRC_TOKEN
import io.lightcone.relayer.integration._
import org.scalatest._

class SubmitOrderSpec_EnoughBalanceNotEnoughAllowance
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with Matchers {

  feature("submit an order") {
    scenario("enough balance and not enough allowance") {
      implicit val account = getUniqueAccount()
      Given(
        s"an new account with enough balance and not enough allowance: ${account.getAddress}"
      )

      addAccountExpects({
        case req =>
          GetAccount.Res(
            Some(
              AccountBalance(
                address = req.address,
                tokenBalanceMap = req.tokens.map { t =>
                  t -> AccountBalance.TokenBalance(
                    token = t,
                    balance = "100000".zeros(LRC_TOKEN.decimals),
                    allowance = "30".zeros(LRC_TOKEN.decimals),
                    availableBalance = "100000".zeros(LRC_TOKEN.decimals),
                    availableAlloawnce = "30".zeros(LRC_TOKEN.decimals)
                  )
                }.toMap
              )
            )
          )
      })

      val getBalanceReq = GetAccount.Req(
        account.getAddress,
        tokens = Seq(LRC_TOKEN.address)
      )
      val res = getBalanceReq.expectUntil(
        check((res: GetAccount.Res) => {
          val lrc_ba = res.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address)
          NumericConversion.toBigInt(lrc_ba.getAllowance) == "30".zeros(
            LRC_TOKEN.decimals
          ) &&
          NumericConversion.toBigInt(lrc_ba.getAvailableAlloawnce) == "30"
            .zeros(LRC_TOKEN.decimals) &&
          NumericConversion.toBigInt(lrc_ba.getBalance) > "100".zeros(
            LRC_TOKEN.decimals
          ) &&
          NumericConversion.toBigInt(lrc_ba.getAvailableBalance) > "100"
            .zeros(LRC_TOKEN.decimals)
        })
      )

      When("submit an order.")

      val order = createRawOrder(amountS = "50".zeros(LRC_TOKEN.decimals))
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
            true
          })
        )

      val reOrder = getOrdersRes.orders.head

      Then(
        s"the status of the order just submitted is ${reOrder.getState.status}"
      )

      getBalanceReq.expect(
        check(
          (res: GetAccount.Res) => {
            val lrc_ba =
              res.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address)
            NumericConversion.toBigInt(lrc_ba.getAvailableAlloawnce) == 0 &&
            NumericConversion
              .toBigInt(lrc_ba.getAvailableBalance) == NumericConversion
              .toBigInt(lrc_ba.getBalance) - "30".zeros(LRC_TOKEN.decimals)
          }
        )
      )

      Then("clear data to avoid other tests being affected")

//     val cancelOrderReq  =  CancelOrder.Req(id =order.hash,owner = account.getAddress,time = NumericConversion.toAmount(BigInt(timeProvider.getTimeSeconds())))
//
//      cancelOrderReq.withSig()

    }

  }
}
