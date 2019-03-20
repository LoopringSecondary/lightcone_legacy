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
import io.lightcone.core.{OrderStatus, RawOrder}
import io.lightcone.lib.ProtoSerializer
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.jsonrpc.JsonSupport
import org.scalatest.matchers.{MatchResult, Matcher}
import scalapb.GeneratedMessage

object AddedMatchers extends JsonSupport {

  val ps = new ProtoSerializer

  def check[T](checkFun: T => Boolean)(implicit m: Manifest[T]) = {
    Matcher { res: T =>
      MatchResult(
        checkFun(res),
        res + " doesn't match",
        res + " matchs"
      )
    }
  }

  def containsInGetOrders(
      orderStatus: OrderStatus,
      hashes: String*
    ) = {
    def findOrder(res: GetOrders.Res) = hashes.map { hash =>
      res.orders.find(_.hash.toLowerCase() == hash.toLowerCase())
    }

    Matcher { res: GetOrders.Res =>
      MatchResult(
        findOrder(res).count(_.nonEmpty) == hashes.size,
        s" ${JsonPrinter.printJsonString(res)} doesn't contains order: $hashes",
        s"${JsonPrinter.printJsonString(res)} contains it."
      )
    } and
      Matcher { res: GetOrders.Res =>
        MatchResult(
          findOrder(res).count(
            _.get.getState.status == orderStatus
          ) == hashes.size,
          s"The status of order:${findOrder(res)
            .map(JsonPrinter.printJsonString)} in result isn't  ${orderStatus}. ",
          "the status matched."
        )
      }
  }

  def outStandingMatcherInGetOrders(
      state: RawOrder.State,
      hash: String
    ) = {
    def findOrder(res: GetOrders.Res) =
      res.orders.find(_.hash.toLowerCase() == hash.toLowerCase())

    Matcher { res: GetOrders.Res =>
      MatchResult(
        findOrder(res).nonEmpty,
        s" ${JsonPrinter.printJsonString(res)} doesn't contains order: $hash",
        s"${JsonPrinter.printJsonString(res)} contains it."
      )
    } and
      Matcher { res: GetOrders.Res =>
        val resState = findOrder(res).get.getState
        val amountS: BigInt = resState.outstandingAmountS
        val amountB: BigInt = resState.outstandingAmountB
        val amountFee: BigInt = resState.outstandingAmountFee
        val expectedAdmountS: BigInt = state.outstandingAmountS
        val expectedAmountB: BigInt = state.outstandingAmountB
        val expectedAmountFee: BigInt = state.outstandingAmountFee
        MatchResult(
          resState.outstandingAmountS == state.outstandingAmountS &&
            resState.outstandingAmountB == state.outstandingAmountB &&
            resState.outstandingAmountFee == state.outstandingAmountFee,
          s"The state of order:${JsonPrinter
            .printJsonString(findOrder(res).get.getState)}, $amountS, $amountB, $amountS in result isn't  ${JsonPrinter
            .printJsonString(state)}, $expectedAdmountS, $expectedAmountB, $expectedAmountFee. ",
          "the state matched."
        )
      }
  }

  def orderBookIsEmpty() = {
    Matcher { res: GetOrderbook.Res =>
      MatchResult(
        res.orderbook.isEmpty || (res.getOrderbook.sells.isEmpty && res.getOrderbook.buys.isEmpty),
        s" ${JsonPrinter.printJsonString(res)} of orderBook nonEmpty.",
        s"${JsonPrinter.printJsonString(res)} of orderBook isEmpty."
      )
    }
  }

  def userFillsIsEmpty() = {
    Matcher { res: GetUserFills.Res =>
      MatchResult(
        res.fills.isEmpty,
        s" ${JsonPrinter.printJsonString(res)} of getUserFills nonEmpty.",
        s"${JsonPrinter.printJsonString(res)} of getUserFills isEmpty."
      )
    }
  }

  def marketFillsIsEmpty() = {
    Matcher { res: GetMarketFills.Res =>
      MatchResult(
        res.fills.isEmpty,
        s" ${JsonPrinter.printJsonString(res)} of GetMarketFills nonEmpty.",
        s"${JsonPrinter.printJsonString(res)} of GetMarketFills isEmpty."
      )
    }
  }

  def accountBalanceMatcher(
      token: String,
      tokenBalance: TokenBalance
    ) = Matcher { res: GetAccount.Res =>
    MatchResult(
      res.getAccountBalance.tokenBalanceMap(token) == tokenBalance,
      s" ${JsonPrinter.printJsonString(res.getAccountBalance.tokenBalanceMap(token))} was not equal to  ${JsonPrinter
        .printJsonString(tokenBalance)}.",
      s"accountBalance matches."
    )
  }

  def resEqual(expectedRes: GeneratedMessage) = Matcher {
    res: GeneratedMessage =>
      MatchResult(
        res == expectedRes,
        s" ${JsonPrinter.printJsonString(res)} was not equal to  ${JsonPrinter.printJsonString(expectedRes)}.",
        s"equals."
      )
  }

}
