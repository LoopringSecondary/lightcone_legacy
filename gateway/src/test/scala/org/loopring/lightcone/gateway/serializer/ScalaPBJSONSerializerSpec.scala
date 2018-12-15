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

package org.loopring.lightcone.gateway.serializer

import org.scalatest._
import org.scalamock.scalatest._
import org.loopring.lightcone.proto._
import org.slf4s.Logging

class ScalaPBJSONSerializerSpec extends FlatSpec with Matchers with Logging {

  val serializer = new ScalaPBJSONSerializer
  val order = new XRawOrder(tokenS = "aaa")
  val json = serializer.serialize(order).get
  println("json: " + json)
  val order2 = serializer.deserialize[XRawOrder](json).get
  order should be(order2)
}
