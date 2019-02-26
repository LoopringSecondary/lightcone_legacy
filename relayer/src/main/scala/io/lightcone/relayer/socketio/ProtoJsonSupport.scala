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

package io.lightcone.relayer.socketio

import java.io.IOException
import java.util

import com.corundumstudio.socketio.AckCallback
import com.corundumstudio.socketio.protocol._
import io.lightcone.core.Amount
import io.lightcone.lib.NumericConversion
import io.netty.buffer._
import org.json4s._
import scalapb.GeneratedMessage
import scalapb.json4s._

class ProtoJsonSupport(delegate: JacksonJsonSupport) extends JsonSupport {

  val formatRegistry =
    JsonFormat.DefaultRegistry
      .registerWriter[Amount](
        (amount: Amount) =>
          JString(
            NumericConversion.toHexString(BigInt(amount.value.toByteArray))
          ), {
          case JString(str) => null // this should never happen
          case _            => throw new JsonFormatException("Expected a string.")
        }
      )

  val printer = new Printer(formatRegistry = formatRegistry)

  @throws[IOException]
  def readAckArgs(
      src: ByteBufInputStream,
      callback: AckCallback[_]
    ): AckArgs =
    delegate.readAckArgs(src, callback)

  @throws[IOException]
  def readValue[T](
      namespaceName: String,
      src: ByteBufInputStream,
      valueType: Class[T]
    ): T =
    delegate.readValue(namespaceName, src, valueType)

  @throws[IOException]
  def writeValue(
      out: ByteBufOutputStream,
      value: scala.AnyRef
    ): Unit = {
    value match {
      case list: util.ArrayList[_] =>
        list.get(1) match {
          case msg: GeneratedMessage =>
            val jsonStr =
              s"""
                ["${list.get(0)}",${printer.print(msg)}]
                """.stripMargin
            out.write(jsonStr.getBytes)
          case _ =>
            delegate.writeValue(out, value)
        }
      case _ =>
        delegate.writeValue(out, value)
    }
  }

  def addEventMapping(
      namespaceName: String,
      eventName: String,
      eventClass: Class[_]*
    ): Unit =
    delegate.addEventMapping(namespaceName, eventName, eventClass: _*)

  def removeEventMapping(
      namespaceName: String,
      eventName: String
    ): Unit =
    delegate.removeEventMapping(namespaceName, eventName)

  def getArrays: util.List[Array[Byte]] = delegate.getArrays

}
