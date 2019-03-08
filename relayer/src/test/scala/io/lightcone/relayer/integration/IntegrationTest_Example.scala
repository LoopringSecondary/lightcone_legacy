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

import akka.pattern._
import akka.util.Timeout
import io.lightcone.core._
import io.lightcone.relayer._
import io.lightcone.relayer.Preparations._
import io.lightcone.relayer.integration.intergration._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.data.{
  AccountBalance,
  BatchGetCutoffs,
  GetAccount,
  GetOrderbook,
  SubmitOrder
}
import io.lightcone.relayer.support.{
  LRC_TOKEN,
  OrderGenerateSupport,
  WETH_TOKEN
}
import org.scalamock.scalatest.MockFactory
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

class IntegrationTest_Example
    extends FlatSpec
    with MockFactory
    with Matchers
    with RpcHelper
    with OrderGenerateSupport {

  "send a GetAccount.Req" must "get right response" in {
    implicit val timeout = Timeout(5.second)
    val account = getUniqueAccount()
    //设置需要的金额
    val req = GetAccount.Req(
      account.getAddress,
      tokens = Seq(LRC_TOKEN.address)
    )
    val ethRes = GetAccount.Res(
      Some(
        AccountBalance(
          address = account.getAddress,
          tokenBalanceMap = Map(
            LRC_TOKEN.address -> AccountBalance
              .TokenBalance(
                token = LRC_TOKEN.address,
                balance = BigInt(1000)
              )
          )
        )
      )
    ) //这个变量不等于AccountManager中返回的数据，AccountManager中返回数据包含available
    (ethAccessDataProvider.getAccount _)
      .expects(where { req2: GetAccount.Req =>
        req2.address == req.address
      })
      .returns(ethRes)
      .atLeastOnce()
    //设置在AccountManager恢复时，需要的数据，cutoff等
    (ethQueryDataProvider.batchGetCutoffs _)
      .expects(*)
      .returns(BatchGetCutoffs.Res())
      .anyNumberOfTimes()
    val res11 = Await.result(entryPointActor ? req, timeout.duration)

    info(s"success, ${res11}")

    //提交订单并检验
    val rawOrder =
      createRawOrder(amountS = "10".zeros(18), amountB = "1".zeros(18))

    SubmitOrder
      .Req(Some(rawOrder))
      .expect(check((res: SubmitOrder.Res) => true))

    GetOrderbook
      .Req(
        0,
        100,
        Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      .expectUntil(check((res: GetOrderbook.Res) => res.orderbook.nonEmpty))

  }

}
