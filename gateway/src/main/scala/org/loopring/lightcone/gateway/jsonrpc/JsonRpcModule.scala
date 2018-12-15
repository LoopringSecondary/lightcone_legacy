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
import akka.http.scaladsl.server._
import akka.http.scaladsl.model._
import org.json4s._
import scala.concurrent.duration._
import org.json4s.JsonAST.JValue
// import StatusCodes._

trait JsonRpcModule extends JsonRpcBinding with JsonSupport {
  val requestHandler: ActorRef
  val endpoint: String = "api"

  implicit val system: ActorSystem
  implicit val timeout = Timeout(2 second)

  val JSON_RPC_VER = "2.0"

  val myExceptionHandler = ExceptionHandler {
    case e: Throwable =>
      extractUri { uri =>
        println(s"Request to $uri could not be handled normally")
        replyWithError(-32603, Some(e.getMessage))("", None)
      }
  }

  val jsonRPCRoutes: Route = handleExceptions(myExceptionHandler) {
    path(endpoint) {
      post {
        entity(as[JsonRpcRequest]) { jsonReq =>
          println("=====json request: " + jsonReq)
          implicit val method = jsonReq.method
          implicit val id = jsonReq.id

          if (id.isEmpty) {
            replyWithError(-32000, Some("`id missing"))
          } else {
            getPayloadSerializer(method) match {
              case None =>
                replyWithError(-32601)

              case Some(ps) =>
                jsonReq.params.map(ps.toRequest) match {
                  case None =>
                    replyWithError(
                      -32602,
                      Some("`params` is missing, use `{}` as default value")
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
  }

  private def replyWithError(
      code: Int,
      message: Option[String] = None,
      data: Option[JValue] = None
    )(
      implicit method: String,
      id: Option[String]
    ) =
    complete(
      JsonRpcResponse(
        JSON_RPC_VER,
        method,
        None,
        Some(JsonRpcError(code, message, data)),
        id
      )
    )

  private def replyWith(
      content: JValue
    )(
      implicit method: String,
      id: Option[String]
    ) =
    complete(JsonRpcResponse(JSON_RPC_VER, method, Option(content), None, id))

}
