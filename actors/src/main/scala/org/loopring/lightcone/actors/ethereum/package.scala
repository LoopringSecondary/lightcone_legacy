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

package org.loopring.lightcone.actors

import org.json4s.{ DefaultFormats, NoTypeHints }
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import org.loopring.lightcone.proto.ethereum._

package object ethereum {

  type ProtoBuf[T] = scalapb.GeneratedMessage with scalapb.Message[T]

  private[ethereum] case class JsonRpcReqWrapped(
      id: Int,
      jsonrpc: String = "2.0",
      method: String,
      params: Any
  ) {
    private implicit val formats = Serialization.formats(NoTypeHints)
    def toProto: XJsonRpcReq = XJsonRpcReq(write(this))
  }

  private[ethereum] case class JsonRpcResWrapped(
      id: Any,
      jsonrpc: String = "2.0",
      result: Any,
      error: Option[XJsonRpcErr]
  )

  private[ethereum] object JsonRpcResWrapped {
    private implicit val formats = DefaultFormats
    def toJsonRpcResWrapped: PartialFunction[XJsonRpcRes, JsonRpcResWrapped] = {
      case j: XJsonRpcRes ⇒ parse(j.json).extract[JsonRpcResWrapped]
    }
  }

}
