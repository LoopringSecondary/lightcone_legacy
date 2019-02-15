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

import akka.actor.{ActorSystem, Cancellable}
import com.corundumstudio.socketio.SocketIOClient
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class WrappedSocketClient[R](
    eventName: String,
    val delayInSeconds: Int = 1,
    val client: SocketIOClient,
    val req: R
  )(
    implicit
    system: ActorSystem,
    ec: ExecutionContext) {

  implicit val formats = DefaultFormats

  var scheduler: Cancellable = null

  def start()(queryData: R => Future[AnyRef]): Unit = {
    scheduler = system.scheduler.schedule(
      0 second,
      delayInSeconds seconds,
      new Runnable {
        override def run(): Unit = {
          if (client.isChannelOpen) {
            queryData(req).map { data =>
              client.sendEvent(
                eventName,
                //使用protobuf时，序列化时会有fields信息。使用case class 或者Map
                parse(Serialization.write(data)).values
                  .asInstanceOf[Map[String, Any]]
              )
            }
          } else {
            stop
          }
        }
      }
    )
  }

  def stop = {
    if (scheduler != null) {
      scheduler.cancel()
      scheduler = null
    }
  }

  def restart()(queryData: R => Future[AnyRef]): Unit = {
    stop
    start()(queryData)
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case _client: WrappedSocketClient[_] =>
        _client.client.getSessionId.equals(client.getSessionId)
      case _ => false
    }
  }
}
