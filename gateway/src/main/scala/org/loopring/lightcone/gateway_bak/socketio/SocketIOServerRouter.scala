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

import akka.actor._
import akka.routing.RoundRobinPool
import scala.concurrent.duration._
import scala.util.Random

class SocketIOServerRouter
  extends Actor
  with Timers
  with ActorLogging {

  implicit val ex = context.system.dispatcher
  private var prevId = 0

  override def receive: Receive = {

    case StartBroadcast(server, eventBindings, pool) ⇒
      log.debug("start check broadcast message")

      eventBindings.bindings.foreach {
        case EventBinding(event, interval, replyTo) ⇒
          val routees = context.actorOf(
            RoundRobinPool(pool).props(Props[SocketIOServerActor]),
            s"socket_timer_${event}_${getNextId}"
          )

          // QUESTION(Toan): IOClient 好像不能序列化吧？这样的消息无法通过actor跨网络发送。
          context.system.scheduler.schedule(
            5 seconds,
            interval seconds,
            routees,
            BroadcastMessage(server, event, replyTo)
          )
      }
  }

  private def getNextId = {
    prevId += 1
    prevId
  }

}
