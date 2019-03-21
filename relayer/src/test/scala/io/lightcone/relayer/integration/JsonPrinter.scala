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
import io.lightcone.lib.ProtoSerializer
import io.lightcone.relayer.jsonrpc.JsonSupport
import scalapb.GeneratedMessage

object JsonPrinter extends JsonSupport {

  val ps = new ProtoSerializer

  def printJsonString(msg: GeneratedMessage) = {
    serialization.write(ps.serialize(msg))
  }

  def printJsonString(msg: Option[GeneratedMessage]) = {
    serialization.write(ps.serialize(msg.get))
  }
}
