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

package io.lightcone.lib


import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import org.json4s.JsonAST.JValue

class ProtoSerializer {

  def serialize[T](value: T): Option[JValue] =
    macro ProtoSerializerMacro.serialize[T]

  def deserialize[T](json: JValue): Option[T] =
    macro ProtoSerializerMacro.deserialize[T]
}

private object ProtoSerializerMacro {

  def serialize[T](
      c: blackbox.Context
    )(value: c.Expr[T]
    ): c.Expr[Option[JValue]] = {
    import c.universe._

    c.Expr[Option[JValue]](q"""
          {
            import scalapb.json4s._
            import com.google.protobuf.ByteString
            import io.lightcone.core.Amount
            import org.json4s.JsonAST.{JString, JValue}
            import scalapb.json4s.{JsonFormat, JsonFormatException}

             val formatRegistry =
                  JsonFormat.DefaultRegistry
                    .registerWriter[Amount](
                      (amount: Amount) =>
                        JString(
                          NumericConversion.toHexString(BigInt(amount.value.toByteArray))
                        ), {
                        case JString(str) =>
                          Amount(
                            value = ByteString
                              .copyFrom(NumericConversion.toBigInt(str).toByteArray)
                          )
                        case _ => throw new JsonFormatException("Expected a string.")
                      }
                    )
            scala.util.Try(new Printer(formatRegistry = formatRegistry)
            .toJson($value))
            .toOption
          }""")
  }

  def deserialize[T: c.WeakTypeTag](
      c: blackbox.Context
    )(json: c.Expr[JValue]
    ): c.Expr[Option[T]] = {
    import c.universe._

    val deserializeType = weakTypeOf[T]

    c.Expr[Option[T]](q"""
          {
            import scalapb.json4s._
            import com.google.protobuf.ByteString
            import io.lightcone.core.Amount
            import org.json4s.JsonAST.{JString, JValue}
            import scalapb.json4s.{JsonFormat, JsonFormatException}

            val formatRegistry =
                 JsonFormat.DefaultRegistry
                   .registerWriter[Amount](
                     (amount: Amount) =>
                       JString(
                         NumericConversion.toHexString(BigInt(amount.value.toByteArray))
                       ), {
                       case JString(str) =>
                         Amount(
                           value = ByteString
                             .copyFrom(NumericConversion.toBigInt(str).toByteArray)
                         )
                       case _ => throw new JsonFormatException("Expected a string.")
                     }
                   )
            scala.util.Try(new Parser(formatRegistry = formatRegistry)
              .fromJson[$deserializeType]($json))
              .toOption
          }""")
  }
}
