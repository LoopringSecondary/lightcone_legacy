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
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._

class ReorgSpec_BurnRate
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("test reorg of burnrate ") {
    scenario("the value of burnrate after forked") {

      //TODO: waiting the pr of refactor of Metadata
      Given("check the init value of GTO's BurnRate")
      val gtoBurnRate = metadataManager
        .getTokenWithSymbol(GTO_TOKEN.symbol)
        .get
        .getMetadata
        .getBurnRate

      Then(s"set the BurnRate of GTO = ${gtoBurnRate} - 0.1 in block = 100")
      TokenBurnRateChangedEvent(
        header = Some(
          EventHeader()
        ),
        token = GTO_TOKEN.address
      )

      Then("dispatch a forked block event")

    }
  }
}
