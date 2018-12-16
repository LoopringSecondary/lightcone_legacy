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
import scalapb.json4s.JsonFormat
import scala.reflect.runtime.universe._
import akka.actor._
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask

trait JsonRpcBinding {

  private var bindings = Map.empty[String, PayloadConverter[_, _]]
  implicit private val module_ = this
  implicit private val ps = new ProtoSerializer

  def ifReceive[T <: Proto[T]: TypeTag] = new Binder[T]

  private[jsonrpc] def addPayloadConverter[
      T <: Proto[T]: TypeTag,
      S <: Proto[S]: TypeTag
    ](method: String,
      ps: PayloadConverter[T, S]
    ) = {
    assert(!bindings.contains(method), s"method ${method} already bound")
    bindings = bindings + (method -> ps)
  }

  def getPayloadConverter(method: String) = bindings.get(method)
}
