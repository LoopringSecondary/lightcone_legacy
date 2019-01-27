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

import org.loopring.lightcone.lib._
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.Serialization
import scala.reflect.ClassTag

import org.loopring.lightcone.proto.ErrorCode

import scala.reflect.runtime.universe.{typeOf, TypeTag}

// Owner: Daniel
class PayloadConverter[T <: AnyRef: TypeTag, S <: AnyRef: TypeTag](
    implicit
    cs: ClassTag[S]) {
  implicit val formats = Serialization.formats(NoTypeHints)

  def fromJson(json: JValue): S = json.extract[S]

  def toJson(s: Any): JValue = {
    s match {
      case err: ErrorException =>
        throw err
      case _ =>
        if (!cs.runtimeClass.isInstance(s))
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"expect ${typeOf[T].typeSymbol.name} get ${s.getClass.getName}"
          )
        Extraction.decompose(s)
    }
  }
}
