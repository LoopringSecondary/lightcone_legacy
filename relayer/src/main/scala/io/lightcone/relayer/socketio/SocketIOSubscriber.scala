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

class SocketIOSubscriber(
    val client: SocketIOClient,
    val subscription: AnyRef) {

  def sendEvent(
      eventName: String,
      event: AnyRef
    ): Unit = {
    if (client.isChannelOpen) {
      client.sendEvent(eventName, event)
    }
  }

  override def equals(obj: Any): Boolean = {
    if (obj == null) false
    else
      obj match {
        case c: SocketIOSubscriber =>
          c.client.getSessionId.equals(client.getSessionId)
        case _ => false
      }
  }
}
