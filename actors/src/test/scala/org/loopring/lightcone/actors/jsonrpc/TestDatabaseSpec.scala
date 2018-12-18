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

package org.loopring.lightcone.actors.jsonrpc

import org.loopring.lightcone.proto.XRawOrder

import scala.concurrent.Await
import scala.concurrent.duration._

class TestDatabaseSpec extends DatabaseSupport {

  "testContainer" must "..." in {
    val owner = "0x-getorder-state0-01"
    val result = for {
      query ← dbModule.orderService.getOrder(owner)
    } yield query
    val res = Await.result(result.mapTo[Option[XRawOrder]], 5.second)
    res should not be empty
  }
}
