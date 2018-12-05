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

package org.loopring.lightcone.gateway_bak

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.loopring.lightcone.gateway_bak.jsonrpc._
import org.loopring.lightcone.gateway_bak.api._
import org.loopring.lightcone.gateway_bak.socketio.SocketIOServer
import org.slf4s.Logging

import scala.concurrent.duration._
import scala.util._

class HttpAndIOServer(
    jsonRpcServer: JsonRpcServer,
    ioServer: SocketIOServer
)(
    implicit
    system: ActorSystem,
    mat: ActorMaterializer
) extends Json4sSupport with Logging {

  // TODO(Duan): Inject this and make it confiburable
  implicit val timeout = Timeout(3 seconds)
  implicit val ex = system.dispatcher

  val config = system.settings.config
  val pathName = config.getString("jsonrpc.http.path")
  val port = config.getInt("jsonrpc.http.port")

  lazy val route =
    pathPrefix(pathName) {
      pathEnd {
        post {
          entity(as[JsonRpcRequest]) { req ⇒
            val f = jsonRpcServer
              .handleRequest(req)
              .map(toJsonResp)
            complete(f)
          }
        }
      }
    }

  private def toJsonResp: PartialFunction[Option[String], HttpResponse] = {
    case Some(str) ⇒
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(ContentTypes.`application/json`, str)
      )
  }

  try {
    ioServer.start
  } catch {
    case t: Throwable ⇒ log.error("socketio started failed!", t)
  }

  Http().bindAndHandle(route, "localhost", port) onComplete {
    case Success(value) ⇒ log.info(s"http server has started @ $value")
    case Failure(ex)    ⇒ log.error("http server failed", ex)
  }
}
