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

class ProtoSerializer {

  def serialize[T](value: T): Option[String] =
    macro ProtoSerializerMacro.serialize[T]

  def deserialize[T](json: String): Option[T] =
    macro ProtoSerializerMacro.deserialize[T]
}

private object ProtoSerializerMacro {

  def serialize[T](
      c: blackbox.Context
    )(value: c.Expr[T]
    ): c.Expr[Option[String]] = {
    import c.universe._

    c.Expr[Option[String]](q"""
          {
            import scalapb.json4s.JsonFormat
            scala.util.Try(JsonFormat.toJsonString($value)).toOption
          }""")
  }

  def deserialize[T: c.WeakTypeTag](
      c: blackbox.Context
    )(json: c.Expr[String]
    ): c.Expr[Option[T]] = {
    import c.universe._

    val deserializeType = weakTypeOf[T]

    c.Expr[Option[T]](q"""
          {
            import scalapb.json4s.JsonFormat
            scala.util.Try(JsonFormat.fromJsonString[$deserializeType]($json)).toOption
          }""")
  }
}
