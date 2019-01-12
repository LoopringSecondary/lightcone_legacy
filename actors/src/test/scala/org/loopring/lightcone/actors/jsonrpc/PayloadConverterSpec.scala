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

import org.loopring.lightcone.lib.ProtoSerializer
import org.loopring.lightcone.proto._
import org.scalatest._
import org.slf4s.Logging

class PayloadConverterSpec extends FlatSpec with Matchers with Logging {
  implicit val ps = new ProtoSerializer()
  val serializer = new PayloadConverter[RawOrder, RawOrder]
  val order = new RawOrder(tokenS = "aaa")

  val json = serializer.convertFromResponse(order)
  val order_ = serializer.convertToRequest(json)
  order_ should be(order)
}
