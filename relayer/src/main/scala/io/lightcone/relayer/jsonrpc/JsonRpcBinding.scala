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
import scala.reflect.runtime.universe._

// Owner: Daniel
trait JsonRpcBinding {

  private var bindings = Map.empty[String, Reply[_, _, _, _]]
  implicit private val module_ = this
  implicit private val ps = new ProtoSerializer

  def method(name: String) = new Method(name)

  private[jsonrpc] def addReply[ //
      A <: Proto[A]: TypeTag, //
      B <: Proto[B]: TypeTag, //
      C <: Proto[C]: TypeTag, //
      D <: Proto[D]: TypeTag //
    ](reply: Reply[A, B, C, D]
    ): Unit = {
    assert(
      !bindings.contains(reply.method),
      s"method ${reply.method} already bound"
    )
    bindings = bindings + (reply.method -> reply)
  }

  def getReply(method: String) = bindings.get(method)
}
