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

import com.corundumstudio.socketio.SocketIOClient

class WrappedSocketClient[R](
    eventName: String,
    val client: SocketIOClient,
    val req: R) {

  def sendEvent(data: AnyRef): Unit = {
    if (client.isChannelOpen)
      client.sendEvent(eventName, data)
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case _client: WrappedSocketClient[_] =>
        _client.client.getSessionId.equals(client.getSessionId)
      case _ => false
    }
  }
}
