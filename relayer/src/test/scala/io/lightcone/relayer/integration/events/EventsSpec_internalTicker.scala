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

import akka.testkit.EventFilter
import io.lightcone.ethereum.persistence._
import org.scalatest._

class EventsSpec_internalTicker
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("test event:internal ticker") {
    scenario("1: ") {

      When("dispatch the OHLCRawData")
      eventDispatcher.dispatch(
        OHLCRawData(
          100L,
          0L,
          "0x1111",
          "0x2222",
          timeProvider.getTimeSeconds(),
          123.1,
          6.7,
          0.054427
        )
      )

      Then("check log")
      Thread.sleep(2000)
      val result = EventFilter
        .info(message = "ohlcRawData stored", occurrences = 1)
        .intercept(() => true)
      log.info(s"--2 $result")
    }
  }

}
