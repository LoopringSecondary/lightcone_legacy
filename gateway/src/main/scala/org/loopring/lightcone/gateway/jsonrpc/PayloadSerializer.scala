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

import scala.reflect.runtime.universe._
import org.loopring.lightcone.lib.ProtoSerializer
import scalapb.json4s.JsonFormat
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.runtime.universe._
import org.json4s.JObject

class PayloadSerializer[T <: Proto[T]: TypeTag, S <: Proto[S]: TypeTag](
    implicit tc: ProtoC[T],
    ts: ProtoC[S],
    ps: ProtoSerializer) {

  def toRequest(str: JObject): T = ps.deserialize[T](str).get

  def fromResponse(s: Any): String = ps.serialize[S](s.asInstanceOf[S]).get
}
