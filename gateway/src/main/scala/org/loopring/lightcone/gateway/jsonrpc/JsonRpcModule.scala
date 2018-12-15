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

package org.loopring.lightcone.gateway.jsonrpc

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import scalapb.json4s.JsonFormat
import scala.reflect.runtime.universe._
import akka.http.scaladsl.Http
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import akka.actor._
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import akka.stream.ActorMaterializer

trait JsonRpcModule extends JsonRpcBinding with JsonSupport {
  val requestHandler: ActorRef

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val ec: ExecutionContext

  private var bindingFuture: Option[Future[Http.ServerBinding]] = None

  implicit val timeout: Timeout
  val JSON_RPC_VER = "2.0"

  val route: Route = {
    path("api") {
      post {
        entity(as[JsonRpcRequest]) { jsonReq =>
          println("=====json request: " + jsonReq)
          implicit val method = jsonReq.method
          implicit val id = jsonReq.id

          getPayloadSerializer(method) match {
            case None =>
              replyWithError(123, s"invalid method `${method}`")

            case Some(ps) =>
              jsonReq.params.map(ps.toRequest) match {
                case None =>
                  replyWithError(
                    123,
                    "`params` is missing, you can provide \"{}\""
                  )

                case Some(req) =>
                  onSuccess(requestHandler ? req) { resp =>
                    replyWith(ps.fromResponse(resp))
                  }
              }
          }
        }
      }
    }
  }

  private def replyWithError(
      code: Int,
      message: String
    )(
      implicit method: String,
      id: Option[String]
    ) =
    complete(
      JsonRpcResponse(
        JSON_RPC_VER,
        method,
        None,
        Some(JsonRpcError(code, Some(message))),
        id
      )
    )

  private def replyWith(
      content: String
    )(
      implicit method: String,
      id: Option[String]
    ) =
    complete(JsonRpcResponse(JSON_RPC_VER, method, Option(content), None, id))

  def start(
      host: String,
      port: Int
    ) = {
    Http().bindAndHandle(route, host, port)
  }

}
