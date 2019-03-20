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

package io.lightcone.relayer.integration.helper
import io.lightcone.core.{Amount, BurnRate}
import io.lightcone.relayer.data.{GetAccount, _}
import io.lightcone.relayer.ethereummock._
import org.scalamock.scalatest.MockFactory

import scala.math.BigInt

//TODO:可以考虑设置请求次数
case class MockExpects[E, T](defaultExpects:PartialFunction[E, T]) {
  var expects = defaultExpects

  def addExpect(expect: PartialFunction[E, T]) = {
    expects = expect orElse expects
  }

  def apply(e:E): T = expects(e)
}

trait MockHelper extends MockFactory {

  private var getAccountExpects:MockExpects[GetAccount.Req, GetAccount.Res] = _
  private var filledAmountExpects:MockExpects[GetFilledAmount.Req, GetFilledAmount.Res] = _
  private var burnRateExpects: MockExpects[GetBurnRate.Req, GetBurnRate.Res] = _
  private var cutoffsExpects: MockExpects[BatchGetCutoffs.Req, BatchGetCutoffs.Res] = _
  private var orderCancelExpects: MockExpects[GetOrderCancellation.Req, GetOrderCancellation.Res] = _

  initExpects()
  def addAccountExpects(expect: PartialFunction[GetAccount.Req, GetAccount.Res] ) = {
    getAccountExpects.addExpect(expect)
  }

  def addFilledAmountExpects(expect: PartialFunction[GetFilledAmount.Req, GetFilledAmount.Res]) = {
    filledAmountExpects.addExpect(expect)
  }

  def addBurnRateExpects(expect: PartialFunction[GetBurnRate.Req, GetBurnRate.Res]) = {
    burnRateExpects.addExpect(expect)
  }

  def addCutoffsExpects(expect: PartialFunction[BatchGetCutoffs.Req, BatchGetCutoffs.Res]) = {
    cutoffsExpects.addExpect(expect)
  }

  def addOrderCancelExpects(expect: PartialFunction[GetOrderCancellation.Req, GetOrderCancellation.Res]) = {
    orderCancelExpects.addExpect(expect)
  }

  //eth的prepare，每次重设，应当有默认值，beforeAll和afterAll都需要重设
  //重设时，不能直接设置新的expects来覆盖旧有的expects，但是可以通过使用新变量或者针对每个expect进行操作，但是后者比较繁琐
  def setDefaultEthExpects() = {
    queryProvider = mock[EthereumQueryDataProvider]
    accessProvider = mock[EthereumAccessDataProvider]
    initExpects()

    //账户余额
    (queryProvider.getAccount _)
      .expects(*)
      .onCall { req: GetAccount.Req =>
        getAccountExpects(req)
      }
      .anyNumberOfTimes()

    //burnRate
    (queryProvider.getBurnRate _)
      .expects(*)
      .onCall({ req: GetBurnRate.Req =>
        burnRateExpects(req)
      })
      .anyNumberOfTimes()

    //batchGetCutoffs
    (queryProvider.batchGetCutoffs _)
      .expects(*)
      .onCall({ req: BatchGetCutoffs.Req =>
        cutoffsExpects(req)
      })
      .anyNumberOfTimes()

    //orderCancellation
    (queryProvider.getOrderCancellation _)
      .expects(*)
      .onCall({ req: GetOrderCancellation.Req =>
          orderCancelExpects(req)
        })
      .anyNumberOfTimes()

    //getFilledAmount
    (queryProvider.getFilledAmount _)
      .expects(*)
      .onCall({ req: GetFilledAmount.Req =>
        filledAmountExpects(req)
      })
      .anyNumberOfTimes()
  }

  private def initExpects() = {
    getAccountExpects = MockExpects[GetAccount.Req, GetAccount.Res]{
      case req => GetAccount.Res(
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
    filledAmountExpects = MockExpects[GetFilledAmount.Req, GetFilledAmount.Res]{
      case req =>
        val zeroAmount: Amount = BigInt(0)
        GetFilledAmount.Res(
          filledAmountSMap = (req.orderIds map { id =>
            id -> zeroAmount
          }).toMap
        )
    }
    burnRateExpects = MockExpects[GetBurnRate.Req, GetBurnRate.Res]{
      case req =>
        GetBurnRate.Res(burnRate = Some(BurnRate()))
    }
    cutoffsExpects = MockExpects[BatchGetCutoffs.Req, BatchGetCutoffs.Res] {
      case req =>
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
    }
    orderCancelExpects = MockExpects[GetOrderCancellation.Req, GetOrderCancellation.Res] {
      case req =>
        GetOrderCancellation.Res(
          cancelled = false,
          block = 100
        )
    }
  }
}
