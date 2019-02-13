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

package io.lightcone.relayer.socket

import akka.actor.ActorSystem
import com.corundumstudio.socketio.SocketIOClient

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class WrappedSocketClient[R](
    eventName: String,
    val client: SocketIOClient,
    req: R
  )(
    implicit
    system: ActorSystem,
    ec: ExecutionContext) {

  def response()(queryData: R => Future[AnyRef]): Unit = {
    system.scheduler.schedule(0 second, 1 seconds, new Runnable {
      override def run(): Unit = {
        queryData(req).map(data => client.sendEvent(eventName, data))
      }
    })
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case _client: WrappedSocketClient[_] =>
        _client.client.getSessionId.equals(client.getSessionId)
      case _ => false
    }
  }
}
