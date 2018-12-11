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

package org.loopring.lightcone.actors.entrypoint

import akka.actor._
import akka.util.Timeout
import akka.event.LoggingReceive
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._

import scala.concurrent.ExecutionContext

object EntryPointActor {
  val name = "entrypoint"
}

class EntryPointActor()(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    actors: Lookup[ActorRef]
) extends Actor with ActorLogging {

  def receive = LoggingReceive {
    case msg: Any ⇒
      findDestination(msg) match {
        case Some(dest) ⇒
          actors.get(dest) forward msg

        case None ⇒
          sender ! XError(error = s"unsupported message: $msg")
          log.debug(s"unsupported msg: $msg")
      }
  }

  def findDestination(msg: Any): Option[String] = msg match {
    case _@ (XSubmitRawOrder) ⇒
      Some(OrderHandlerActor.name)

    case _@ (
      XSubmitOrderReq |
      XGetOrderbookReq) ⇒ Some(AccountManagerActor.name)

    case _ ⇒ None
  }

}
