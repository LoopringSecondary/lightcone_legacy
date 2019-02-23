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

package io.lightcone.relayer

import io.lightcone.core._
import io.lightcone.relayer.data.{AccountBalance, BatchGetCutoffs, GetAccount}
import akka.pattern._
import io.lightcone.ethereum.event.AddressBalanceUpdatedEvent

import scala.concurrent.Await

// Please make sure in `mysql.conf` all database dals use the same database configuration.
//todo(hongyu):暂时去掉，需要确认mysql、postgres、ethereum等的启动问题
class IntegrationTest_Example extends IntegrationTest with testing.Constants {

  "send a GetAccount.Req" must "get right response" in {
    //设置需要的金额
    val req = GetAccount.Req(
      "0x00000000000000000000000000000000aaaaabbb",
      tokens = Seq("0x0aaa0000000000000000000000000000000aaaaa")
    )
    val ethRes = GetAccount.Res(
      Some(
        AccountBalance(
          address = "0x00000000000000000000000000000000aaaaabbb",
          tokenBalanceMap = Map(
            "0xaaa0000000000000000000000000000000aaaaa" -> AccountBalance
              .TokenBalance(
                token = "0xaaa0000000000000000000000000000000aaaaa",
                balance = BigInt(1000)
              )
          ),
          nonce = 10000
        )
      )
    ) //这个变量不等于AccountManager中返回的数据，AccountManager中返回数据包含available
    (ethQueryDataProvider.getAccount _)
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
    val res11 = Await.result(entrypoint ? req, timeout.duration)

    //发送事件，只是示例，但是没有检查结果
    val event = AddressBalanceUpdatedEvent()
    eventDispatcher.dispatch(event)

    info(s"success, ${res11}")
  }
}
