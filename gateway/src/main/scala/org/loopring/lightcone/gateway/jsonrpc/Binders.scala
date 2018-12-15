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
import org.loopring.lightcone.gateway.jsonrpc.serialization._
import org.loopring.lightcone.proto._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import scalapb.json4s.JsonFormat
import scala.reflect.runtime.universe._
import akka.actor._
import akka.util.Timeout

trait AbstractJsonRpcModule {

  implicit private val module = this
  implicit private val se = ScalaPBJSONSerializer()

  private var bindings = Map.empty[String, AnyRef]

  def bind[T <: scalapb.GeneratedMessage with scalapb.Message[T]: TypeTag] =
    new Binder[T]

  def addBinded(
      key: String,
      binded: AnyRef
    ) = {
    bindings = bindings + (key -> binded)
  }

  def getBinded(key: String): Binded[_, _] =
    bindings(key).asInstanceOf[Binded[_, _]]
}

class Binder[T <: scalapb.GeneratedMessage with scalapb.Message[T]: TypeTag](
    implicit se: ScalaPBJSONSerializer,
    module: AbstractJsonRpcModule) {

  def to[S <: scalapb.GeneratedMessage with scalapb.Message[S]: TypeTag](
      key: String
    )(
      implicit tc: scalapb.GeneratedMessageCompanion[T],
      ts: scalapb.GeneratedMessageCompanion[S]
    ) = new Binded[T, S](key)
}

class Binded[
    T <: scalapb.GeneratedMessage with scalapb.Message[T]: TypeTag,
    S <: scalapb.GeneratedMessage with scalapb.Message[S]: TypeTag
  ](key: String
  )(
    implicit tc: scalapb.GeneratedMessageCompanion[T],
    ts: scalapb.GeneratedMessageCompanion[S],
    se: ScalaPBJSONSerializer,
    module: AbstractJsonRpcModule) {

  module.addBinded(key, this)

  def strToReq(str: String): T = se.deserialize[T](str).get
  def strToRes(str: String): S = se.deserialize[S](str).get

  def reqToStr(t: Any): String = se.serialize[T](t.asInstanceOf[T]).get
  def resToStr(s: Any): String = se.serialize[S](s.asInstanceOf[S]).get
}
