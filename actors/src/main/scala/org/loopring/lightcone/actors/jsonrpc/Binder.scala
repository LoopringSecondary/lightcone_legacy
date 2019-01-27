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
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import scala.reflect.runtime.universe._
import akka.actor._
import akka.util.Timeout
import scala.reflect._

// Owner: Daniel
class Binder[T0 <: AnyRef: Manifest, T <: AnyRef: Manifest](
    method: String
  )(
    implicit
    module: JsonRpcBinding) {

  def replies[S <: AnyRef: Manifest, S0 <: AnyRef: Manifest] = {
    module.addPayloadConverter(method, new PayloadConverter[T0, T, S, S0])
  }

}
