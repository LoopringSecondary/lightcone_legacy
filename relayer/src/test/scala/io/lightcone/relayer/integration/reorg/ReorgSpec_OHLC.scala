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

import io.lightcone.ethereum.event._
import io.lightcone.ethereum.persistence.{Interval, OHLCRawData}
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._
import org.scalatest.matchers._

class ReorgSpec_OHLC
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("test reorg of OHLC ") {
    scenario("the market history after forked") {

      Given("three OHLC event in different block")
      val currentTime = 1553856200
      val ohlcData = Seq(
        OHLCRawData(
          blockHeight = 100,
          ringIndex = 100,
          txHash =
            "0x03c90be4c23abe114cbcc5b8cf7a0418ca35a70c0859ffdcfc9c4d26d422a03f",
          marketHash = LRC_WETH_MARKET.getMarketPair.hashString,
          time = currentTime - 20,
          baseAmount = 100,
          quoteAmount = 0.1,
          price = BigDecimal(100 / 0.1)
            .setScale(LRC_WETH_MARKET.priceDecimals)
            .doubleValue()
        ),
        OHLCRawData(
          blockHeight = 101,
          ringIndex = 101,
          txHash =
            "0x13c90be4c23abe114cbcc5b8cf7a0418ca35a70c0859ffdcfc9c4d26d422a03f",
          marketHash = LRC_WETH_MARKET.getMarketPair.hashString,
          time = currentTime - 1,
          baseAmount = 200,
          quoteAmount = 0.1,
          price = BigDecimal(200 / 0.1)
            .setScale(LRC_WETH_MARKET.priceDecimals)
            .doubleValue()
        ),
        OHLCRawData(
          blockHeight = 200,
          ringIndex = 200,
          txHash =
            "0x23c90be4c23abe114cbcc5b8cf7a0418ca35a70c0859ffdcfc9c4d26d422a03f",
          marketHash = LRC_WETH_MARKET.getMarketPair.hashString,
          time = currentTime,
          baseAmount = 300,
          quoteAmount = 0.1,
          price = BigDecimal(300 / 0.1)
            .setScale(LRC_WETH_MARKET.priceDecimals)
            .doubleValue()
        )
      )

      ohlcData.map(eventDispatcher.dispatch)
      val req = GetMarketHistory
        .Req(
          marketPair = LRC_WETH_MARKET.marketPair,
          interval = Interval.OHLC_INTERVAL_ONE_MINUTES,
          beginTime = currentTime - 30,
          endTime = currentTime + 10
        )

      val res = req
        .expectUntil(
          check(
            (res: GetMarketHistory.Res) =>
              res.data.nonEmpty &&
                res.data(0).data(1) == ohlcData
                  .foldLeft(0.0)(_ + _.baseAmount)
          )
        )
      Then("dispatch a forked event")
      val blockEvent = BlockEvent(blockNumber = 101)
      Then("check the fill should be deleted from db.")
      eventDispatcher.dispatch(blockEvent)

      Then("check the result of OHLC")
      val res1 = req
        .expectUntil(
          Matcher { res: GetMarketHistory.Res =>
            MatchResult(
              res.data.nonEmpty && res.data(0).data(1) == 100.0,
              s" ${JsonPrinter.printJsonString(res)} value is not 100",
              s"${JsonPrinter.printJsonString(res)} value is 100."
            )
          }
        )
    }
  }
}
