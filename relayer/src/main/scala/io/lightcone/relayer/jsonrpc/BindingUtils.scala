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

package io.lightcone.relayer.jsonrpc
import io.lightcone.lib.ProtoSerializer
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import io.lightcone.lib._
import org.json4s.JsonAST.JValue
import io.lightcone.core._
import scala.reflect.runtime.universe.{typeOf, TypeTag}

// A: External request type
// B: Internal reqeust type
// C: Internal response type
// D: External response type
class Method(
    method: String
  )(
    implicit
    module: JsonRpcBinding,
    ps: ProtoSerializer) {
  private def identity[T <: Proto[T]: TypeTag](obj: T) = obj

  def accepts[A <: Proto[A]: TypeTag](
      implicit
      pa: ProtoC[A],
      ca: ClassTag[A]
    ) = new Accept[A, A](method, identity)

  def accepts[
      A <: Proto[A]: TypeTag, //
      B <: Proto[B]: TypeTag
    ](
      implicit
      pa: ProtoC[A],
      ca: ClassTag[A],
      requestConverter: A => B
    ) =
    new Accept[A, B](method, requestConverter)
}

class Accept[
    A <: Proto[A]: TypeTag, //
    B <: Proto[B]: TypeTag
  ](method: String,
    requestConverter: A => B
  )(
    implicit
    module: JsonRpcBinding,
    ps: ProtoSerializer) {

  private def identity[T <: Proto[T]: TypeTag](obj: T) = obj

  def replies[C <: Proto[C]: TypeTag](
      implicit
      pa: ProtoC[A],
      ca: ClassTag[A],
      pd: ProtoC[C],
      cd: ClassTag[C],
      responseCleaner: C => C = null
    ) =
    new Reply[A, B, C, C](method, requestConverter, identity, responseCleaner)

  def replies[
      C <: Proto[C]: TypeTag, //
      D <: Proto[D]: TypeTag
    ](
      implicit
      pa: ProtoC[A],
      ca: ClassTag[A],
      pc: ProtoC[C],
      cc: ClassTag[C],
      pd: ProtoC[D],
      cd: ClassTag[D],
      responseConverter: C => D
    ) =
    new Reply[A, B, C, D](method, requestConverter, responseConverter, null)
}

class Reply[
    A <: Proto[A]: TypeTag, //
    B <: Proto[B]: TypeTag, //
    C <: Proto[C]: TypeTag, //
    D <: Proto[D]: TypeTag
  ](val method: String,
    requestConverter: A => B,
    responseConverter: C => D,
    responseCleaner: D => D
  )(
    implicit
    pa: ProtoC[A],
    ca: ClassTag[A],
    pc: ProtoC[C],
    cc: ClassTag[C],
    pd: ProtoC[D],
    cd: ClassTag[D],
    module: JsonRpcBinding,
    ps: ProtoSerializer) {

  module.addReply(this)

  def jsonToInternalRequest(str: JValue): B =
    requestConverter(ps.deserialize[A](str).get)

  def jsonToExternalResponse(str: JValue): D =
    ps.deserialize[D](str).get

  def internalResponseToJson(s: Any): JValue = {
    s match {
      case err: ErrorException =>
        throw err
      case _ =>
        if (!cc.runtimeClass.isInstance(s))
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"expect ${typeOf[D].typeSymbol.name} get ${s.getClass.getName}"
          )
        val c = s.asInstanceOf[C]
        val d = Option(responseCleaner) match {
          case Some(f) => f(responseConverter(c))
          case None    => responseConverter(c)
        }
        ps.serialize[D](d).get

    }
  }
}
