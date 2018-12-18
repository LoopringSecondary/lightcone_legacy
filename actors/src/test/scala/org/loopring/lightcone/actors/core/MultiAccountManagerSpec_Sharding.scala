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

package org.loopring.lightcone.actors.core

import org.loopring.lightcone.proto._
import akka.pattern._
import akka.testkit.TestProbe
import org.loopring.lightcone.actors.support.{CommonSpec, MultiAccountManagerSupport}

import scala.concurrent.Await
import scala.concurrent.duration._

class MultiAccountManagerSpec_Sharding
    extends CommonSpec("""akka.cluster.roles=["multi_account_manager"]""")
    with MultiAccountManagerSupport {

  val marketManagerProbe = new TestProbe(system, MarketManagerActor.name) {

    def expectQuery() = expectMsgPF(120 second) {
      case req @ XCancelOrderReq(_, orderId, _, _, _) =>
        log.info(s"##### AM ${req}， ${sender()}")
        sender ! XCancelOrderRes(id = orderId)
    }

  }
  actors.del(MarketManagerActor.name)
  actors.add(MarketManagerActor.name, marketManagerProbe.ref)

  "send a request" must {
    "create an AccountManager and be received by it" in {
      //todo:此处需要进一步测试分片的正确性
      val cancelReq = XCancelOrderReq("0x11111", "0xaaaaa")
      val f = actors.get(MultiAccountManagerActor.name) ? cancelReq
      val res = Await.result(f, timeout.duration)
      info(s"return is : ${res}")
    }
  }

}
