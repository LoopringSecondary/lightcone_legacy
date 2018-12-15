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

package org.loopring.lightcone.gateway.jsonrpc

import org.loopring.lightcone.lib.ProtoSerializer
import org.loopring.lightcone.proto._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import scalapb.json4s.JsonFormat
import scala.reflect.runtime.universe._
import akka.actor._
import akka.util.Timeout

trait JsonRpcBinding {

  private var bindings = Map.empty[String, PayloadSerializer[_, _]]
  implicit private val module_ = this
  implicit private val ps: ProtoSerializer = new ProtoSerializer

  def bindRequest[T <: Proto[T]: TypeTag] = new Binder[T]

  private[jsonrpc] def addPayloadSerializer[
      T <: Proto[T]: TypeTag,
      S <: Proto[S]: TypeTag
    ](method: String,
      ps: PayloadSerializer[T, S]
    ) = {
    assert(!bindings.contains(method), s"method ${method} already bound")
    bindings = bindings + (method -> ps)
  }

  def getPayloadSerializer(method: String) = bindings.get(method)
}
