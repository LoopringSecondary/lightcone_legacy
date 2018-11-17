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

package org.loopring.lightcone.gateway.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.loopring.lightcone.gateway.jsonrpc.{ JsonRpcRequest, JsonRpcServer }
import org.loopring.lightcone.gateway.socketio.SocketIOServer
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

class HttpAndIOServer(
    jsonRpcServer: JsonRpcServer,
    ioServer: SocketIOServer
)(
    implicit
    system: ActorSystem,
    mat: ActorMaterializer
) extends Json4sSupport {

  lazy val logger = LoggerFactory.getLogger(getClass)

  implicit val ex = system.dispatcher

  lazy val pathName = system.settings.config.getString("jsonrpc.http.path")

  implicit val timeout = Timeout(3 seconds)
  lazy val route =
    pathPrefix(pathName) {
      pathEnd {
        post {
          entity(as[JsonRpcRequest]) { req ⇒
            val f = jsonRpcServer.handleRequest(req).map(toApplicationJsonResponse)
            complete(f)
          }
        }
      }
    }

  def toApplicationJsonResponse: PartialFunction[Option[String], HttpResponse] = {
    case Some(s) ⇒
      HttpResponse(
        StatusCodes.OK,
        entity =
          HttpEntity(ContentTypes.`application/json`, s)
      )
  }

  try ioServer.start
  catch {
    case t: Throwable ⇒ logger.error("socketio started failed!", t)
  }

  lazy val bind = Http().bindAndHandle(route, "localhost", system.settings.config.getInt("jsonrpc.http.port"))

  bind onComplete {
    case scala.util.Success(value) ⇒ logger.info(s"http server has started @ $value")
    case scala.util.Failure(ex)    ⇒ logger.error("http server failed", ex)
  }

}
