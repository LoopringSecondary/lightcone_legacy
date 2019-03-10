/*

  Copyright 2017 Loopring Project Ltd (Loopring Foundation).

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

*/
package io.lightcone.relayer.integration
import io.lightcone.relayer.data._
import io.lightcone.relayer._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

//eth的prepare，每次重设，应当有默认值，beforeAll和afterAll都需要重设
trait MockHelper extends WordSpecLike with MockFactory with BeforeAndAfterAll {
  override protected def beforeAll(): Unit = {
    ethQueryDataProvider = mock[EthereumQueryDataProvider]
    ethAccessDataProvider = mock[EthereumAccessDataProvider]
    setDefaultExpects()
    super.beforeAll()
  }

  def setDefaultExpects() = {
    //账户余额
    (ethQueryDataProvider.getAccount _)
      .expects(*)
      .onCall { req: GetAccount.Req =>
        GetAccount.Res(
          Some(
            AccountBalance(
              address = req.address,
              tokenBalanceMap = req.tokens.map { t =>
                t -> AccountBalance.TokenBalance(
                  token = t,
                  balance = BigInt("1000000000000000000000"),
                  allowance = BigInt("1000000000000000000000"),
                  availableAlloawnce = BigInt("1000000000000000000000"),
                  availableBalance = BigInt("1000000000000000000000")
                )
              }.toMap
            )
          )
        )
      }
      .anyNumberOfTimes()

    //burnRate
    (ethQueryDataProvider.getBurnRate _)
      .expects(*)
      .onCall({ req: GetBurnRate.Req =>
        GetBurnRate.Res(burnRate = Some(metadataManager.getBurnRate(req.token)))
      })
      .anyNumberOfTimes()

    //batchGetCutoffs
    (ethQueryDataProvider.batchGetCutoffs _)
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
    //
  }
}
