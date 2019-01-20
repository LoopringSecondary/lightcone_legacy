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

package org.loopring.lightcone.actors.deploy

import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import org.loopring.lightcone.actors.jsonrpc.JsonRpcRequest
import org.loopring.lightcone.actors.support.OrderGenerateSupport
import org.loopring.lightcone.proto._
import org.scalatest.{Matchers, WordSpec}
import org.loopring.lightcone.actors.support._
import scalapb.json4s.JsonFormat

//todo:后续继续测试完善
class DeploySpec extends WordSpec with Matchers with OrderGenerateSupport {

  "send an orderbook request" must {
    "receive a response without value" in {

      val amountS = "10"
      val amountB = "1"
      val rawOrder =
        createRawOrder(amountS = amountS.zeros(18), amountB = amountB.zeros(18))
      val json = SubmitOrder.Req(Some(rawOrder)) match {
        case m: scalapb.GeneratedMessage => JsonFormat.toJson(m)
      }
      val reqJson = JsonRpcRequest("2.0", "submit_order", Some(json), Some("1"))
      implicit val formats = Serialization.formats(NoTypeHints)
      val a = Serialization.write(reqJson)
      info(a)

    }
  }
}
