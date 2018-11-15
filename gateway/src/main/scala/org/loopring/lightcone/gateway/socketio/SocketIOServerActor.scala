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

package org.loopring.lightcone.gateway.socketio

import akka.actor.{ Actor, ActorLogging }

class SocketIOServerActor extends Actor with ActorLogging {

  override def receive: Receive = {
    case BroadcastMessage(server, event, replyTo) ⇒

      log.info(s"${context.self.path} for event:${event} broadcast to clients")

      SocketIOClient.getClients(_ == event).foreach {
        case SubscriberEvent(client, _, json: String) ⇒
          server.invoke(json).foreach(client.sendEvent(replyTo, _))
      }
  }
}
