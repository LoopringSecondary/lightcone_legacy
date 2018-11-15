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

import akka.actor.{ Actor, ActorLogging, Props, Timers }
import akka.routing.RoundRobinPool

import scala.concurrent.duration._

import scala.util.Random

class SocketIOServerRouter extends Actor with Timers with ActorLogging {

  implicit val ex = context.system.dispatcher

  override def receive: Receive = {
    case StartBroadcast(server, eventRegistering, pool) ⇒

      log.info("start check broadcast message")

      for {
        EventRegister(event, interval, replyTo) ← eventRegistering.events

        routees = context.actorOf(
          RoundRobinPool(pool).props(Props[SocketIOServerActor]),
          s"socket_timer_${event}_${Random.nextInt()}")

        msg = BroadcastMessage(server, event, replyTo)

      } yield {

        context.system.scheduler.schedule(5 seconds, interval seconds, routees, msg)

      }

  }

}
