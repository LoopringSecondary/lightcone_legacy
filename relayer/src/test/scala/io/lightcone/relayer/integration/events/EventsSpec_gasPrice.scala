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
import io.lightcone.ethereum.event.BlockGasPricesExtractedEvent
import io.lightcone.relayer.data.GetGasPrice
import org.scalatest._
import akka.pattern._
import scala.concurrent.Await

class EventsSpec_gasPrice
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("test event:gas price") {
    scenario("1: ") {

      When("dispatch the BlockGasPricesExtractedEvent")
      val gasPrice = ((0 until 5) map { i =>
        20L
      }) ++ ((0 until 30) map { i =>
        15L
      }) ++ ((0 until 30) map { i =>
        10L
      }) ++ ((0 until 30) map { i =>
        5L
      }) ++ ((0 until 5) map { i =>
        1L
      })
      eventDispatcher.dispatch(
        BlockGasPricesExtractedEvent(gasPrices = gasPrice.toSeq)
      )

      Then("check gas price")
      Thread.sleep(2000)
      val result = Await.result(
        (entryPointActor ? GetGasPrice.Req()).mapTo[GetGasPrice.Res],
        timeout.duration
      )
      val res: BigInt = result.gasPrice
      res should be(BigInt(10))
    }
  }

}
