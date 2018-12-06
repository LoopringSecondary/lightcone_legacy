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

package org.loopring.lightcone.gateway_bak.socketio

import org.slf4s.Logging
import com.corundumstudio.socketio.listener._

class ConnectionListener
  extends ConnectListener
  with Logging {

  override def onConnect(client: IOClient): Unit = {
    val addr = client.getRemoteAddress
    log.info(s"SocketIO: remote $addr has connected")
  }
}

class DisconnectionListener
  extends DisconnectListener
  with Logging {

  override def onDisconnect(client: IOClient): Unit = {
    SocketIOClient.remove(client)
    val addr = client.getRemoteAddress
    log.info(s"SocketIO: remote $addr has disconnected")
  }
}

