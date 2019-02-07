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

package io.lightcone.relayer.support

import akka.testkit.TestProbe
import io.lightcone.relayer.actors.MarketManagerActor
import io.lightcone.relayer.data._
import io.lightcone.core._

trait MarketManagerMockSupport {
  me: CommonSpec =>

  val marketManagerProbe = new TestProbe(system, MarketManagerActor.name) {

    def expectQuery() = expectMsgPF() {
      case SubmitSimpleOrder(addr, Some(order)) =>
        info(s"received SubmitOrder.Req: ${addr}, ${order}")
    }

    def replyWith() =
      reply(SubmitOrder.Res())
  }
  actors.del(MarketManagerActor.name)
  actors.add(MarketManagerActor.name, marketManagerProbe.ref)

}
