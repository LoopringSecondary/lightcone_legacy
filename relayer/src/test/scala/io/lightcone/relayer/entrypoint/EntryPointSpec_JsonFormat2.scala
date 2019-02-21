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

package io.lightcone.relayer.entrypoint

import com.google.protobuf.ByteString
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.data.BalanceAndAllowance
import org.json4s.JsonAST.JString
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse
import org.json4s.{jackson, CustomSerializer, JNothing, JNull, NoTypeHints}
import org.scalatest.WordSpec
import scalapb.json4s.JsonFormat

private class ByteStringSerializer
    extends CustomSerializer[ByteString](
      _ =>
        ({
          case JString(str) =>
            println(str)
            ByteString.copyFrom(NumericConversion.toBigInt(str).toByteArray)
        }, {
          case bs: ByteString =>
            JString(
              NumericConversion.toHexString(NumericConversion.toBigInt(bs))
            )
        })
    )

private class EmptyValueSerializer
    extends CustomSerializer[String](
      _ =>
        ({
          case JNull => ""
        }, {
          case "" => JNothing
        })
    )

class EntryPointSpec_JsonFormat2 extends WordSpec {

  "using the bytestring type in protobuf" must {
    "it can be serialization and deserialization" in {
      implicit val serialization = jackson.Serialization
      implicit val formats = org.json4s.native.Serialization
        .formats(NoTypeHints) + new ByteStringSerializer + new EmptyValueSerializer

      val value = ByteString.copyFrom(BigInt("f" * 64, 16).toByteArray)
      val ba = BalanceAndAllowance(
        balance = value,
        allowance = value
      )
      val baStr = serialization.write(ba)
      println(baStr)
      info(baStr)
      val ba1 = JsonFormat.fromJsonString[BalanceAndAllowance](baStr)
      println(ba1)
    }
  }

}
