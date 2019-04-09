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

import io.lightcone.core._
import io.lightcone.ethereum.persistence.{Interval, OHLCRawData}
import io.lightcone.relayer.data.GetMarketHistory
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._
import org.scalatest.matchers.{MatchResult, Matcher}

class EventsSpec_kline
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("test event:kline") {
    scenario("1: ") {

      When("dispatch the OHLCRawData")
      val marketPair = MarketPair(
        LRC_TOKEN.address,
        WETH_TOKEN.address
      )
      val now = timeProvider.getTimeSeconds()
      Seq(
        OHLCRawData(
          96L,
          0L,
          "0x1111",
          MarketHash(
            marketPair
          ).hashString,
          now,
          100.0,
          0.1,
          0.0001
        ),
        OHLCRawData(
          101L,
          0L,
          "0x3333",
          MarketHash(
            marketPair
          ).hashString,
          now + 75,
          150,
          0.2,
          0.0013
        ),
        OHLCRawData(
          102L,
          0L,
          "0x3333",
          MarketHash(
            marketPair
          ).hashString,
          now + 90,
          150,
          0.2,
          0.0013
        )
      ).foreach(eventDispatcher.dispatch)

      GetMarketHistory
        .Req(
          Some(marketPair),
          Interval.OHLC_INTERVAL_ONE_MINUTES,
          now - 1000,
          now + 2600
        )
        .expectUntil(
          Matcher { res: GetMarketHistory.Res =>
            MatchResult(
              res.data.length == 2,
              s"kline data length:${res.data.length} not equals 2",
              s"kline data length verified"
            )
          } and
            Matcher { res: GetMarketHistory.Res =>
              val result = res.data.exists { r =>
                r.data(1) == 300.0 && r.data(2) == 0.4 && r
                  .data(3) == 0.0013 && r.data(4) == 0.0013 && r
                  .data(5) == 0.0013 && r.data(6) == 0.0013
              } && res.data.exists { r =>
                r.data(1) == 100.0 && r.data(2) == 0.1 && r
                  .data(3) == 0.0001 && r.data(4) == 0.0001 && r
                  .data(5) == 0.0001 && r.data(6) == 0.0001
              }
              MatchResult(
                result,
                s"kline data of quality not verified",
                s"kline data of quality verified"
              )
            }
        )
    }
  }

}
