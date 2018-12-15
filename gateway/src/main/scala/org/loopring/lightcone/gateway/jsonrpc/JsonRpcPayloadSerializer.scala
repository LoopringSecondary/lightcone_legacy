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

class PayloadSerializer[
    T <: scalapb.GeneratedMessage with scalapb.Message[T]: TypeTag,
    S <: scalapb.GeneratedMessage with scalapb.Message[S]: TypeTag
  ](
    implicit tc: scalapb.GeneratedMessageCompanion[T],
    ts: scalapb.GeneratedMessageCompanion[S],
    ps: ProtoSerializer) {

  def strToReq(str: String): T = ps.deserialize[T](str).get

  def respToStr(s: Any): String = ps.serialize[S](s.asInstanceOf[S]).get
}
