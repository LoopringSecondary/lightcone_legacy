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

package org.loopgring.lightcone.actors.core

import akka.actor.ActorRef
import akka.testkit.TestActorRef
import org.loopgring.lightcone.actors.core.CoreActorsIntegrationCommonSpec._
import org.loopring.lightcone.actors.core.{ AccountManagerActor, MarketManagerActor }
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode.{ ERR_OK, ERR_UNKNOWN }
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.deployment.XActorDependencyReady

class CoreActorsIntegrationSpec_MarketManagerRecovery
  extends CoreActorsIntegrationCommonSpec(XMarketId(GTO_TOKEN.address, WETH_TOKEN.address)) {

  "when an marketManager starts" must {
    "first recover it and then receive order" in {

      val marketManagerActorRecovery: ActorRef = TestActorRef(
        new MarketManagerActor(
          XMarketId(GTO_TOKEN.address, WETH_TOKEN.address),
          config,
          skipRecovery = false
        )
      )

      marketManagerActorRecovery ! XActorDependencyReady(Seq(
        orderDdManagerActor.path.toString,
        gasPriceActor.path.toString,
        orderbookManagerActor.path.toString,
        settlementActor.path.toString
      ))

    }
  }
}
