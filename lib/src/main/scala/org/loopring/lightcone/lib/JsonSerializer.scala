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

package org.loopring.lightcone.lib

import scala.reflect.runtime.universe._

import scalapb.json4s.JsonFormat
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.runtime.universe._
import org.json4s.JsonAST.JValue
import org.json4s._

class CaseClassJsonSerializer {
  implicit val formats = DefaultFormats
  def serialize[T](value: T) = Extraction.decompose(value)
  def deserialize[T: Manifest](json: JValue) = json.extract[T]
}

class ProtoJsonSerializer {

  def serialize[T](value: T): JValue =
    macro ProtoJsonSerializerMacro.serialize[T]

  def deserialize[T](json: JValue): T =
    macro ProtoJsonSerializerMacro.deserialize[T]
}

private object ProtoJsonSerializerMacro {

  def serialize[T](c: blackbox.Context)(value: c.Expr[T]): c.Expr[JValue] = {
    import c.universe._

    c.Expr[JValue](q"""
          {
            import scalapb.json4s.JsonFormat
            JsonFormat.toJson($value)
          }""")
  }

  def deserialize[T: c.WeakTypeTag](
      c: blackbox.Context
    )(json: c.Expr[JValue]
    ): c.Expr[T] = {
    import c.universe._

    val deserializeType = weakTypeOf[T]

    c.Expr[T](q"""
          {
            import scalapb.json4s.JsonFormat
            JsonFormat.fromJson[$deserializeType]($json)
          }""")
  }
}
