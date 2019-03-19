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

package io.lightcone.relayer.support

import io.lightcone.relayer.actors.SocketIONotificationActor
import io.lightcone.relayer.socketio._
import org.scalatest.BeforeAndAfterAll

trait SocketSupport extends BeforeAndAfterAll {
  com: CommonSpec =>

  override def afterAll: Unit = {
    socketServer.server.stop()
    super.afterAll()
  }

  implicit val relayerNotifier = new RelayerNotifier()

  actors.add(SocketIONotificationActor.name, SocketIONotificationActor.start)

  val socketServer = new SocketServer()
  socketServer.start()

  println("start socket server......")
}
