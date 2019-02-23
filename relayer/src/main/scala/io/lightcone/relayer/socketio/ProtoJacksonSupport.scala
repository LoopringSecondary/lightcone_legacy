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

import com.corundumstudio.socketio.protocol.JacksonJsonSupport
import com.google.protobuf.ByteString
import io.lightcone.core.Amount
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.jsonrpc.Proto
import io.netty.buffer.ByteBufOutputStream
import org.json4s.JsonAST.JString
import org.json4s.jackson.Serialization
import scalapb.json4s.{JsonFormat, JsonFormatException}

class ProtoJacksonSupport extends JacksonJsonSupport {

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

  override def writeValue(
      out: ByteBufOutputStream,
      value: scala.Any
    ): Unit = {
    value match {
      case _: Proto[_] =>
        Serialization.write(value,out)
      case _ => super.writeValue(out,value)
    }
  }
}
