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

package org.loopgring.lightcone.actors.marketcap

import akka.actor.Props
import org.loopgring.lightcone.actors.MySepc
import org.loopring.lightcone.actors.marketcap.MarketCapActor

class MarketCapActorSpec extends MySepc {

  // sbt "actors/testOnly *MarketCapActorSpec -- -z test1"
  "test1" in {
    val echo = system.actorOf(Props[MarketCapActor], "test")

    echo ! "hello world"

    assert(1 == 1)
  }

}
