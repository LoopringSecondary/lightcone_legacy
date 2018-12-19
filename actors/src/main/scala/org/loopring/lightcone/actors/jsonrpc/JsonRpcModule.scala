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

package org.loopring.lightcone.actors.jsonrpc

import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto.{XError, XJsonRpcReq, XJsonRpcRes}
import org.json4s._
import org.json4s.JsonAST.JValue
import scalapb.json4s.JsonFormat
import akka.http.scaladsl.Http
import akka.actor._
import akka.util.Timeout
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import org.json4s.jackson.Serialization

import scala.reflect.runtime.universe._
import scala.concurrent.duration._
import scala.concurrent._

trait JsonRpcModule extends JsonRpcBinding with JsonSupport {
  val requestHandler: ActorRef
  val endpoint: String = "jsonrpc"

  implicit val system: ActorSystem
  implicit val timeout = Timeout(2 second)
  implicit val ec = ExecutionContext.global

  val JSON_RPC_VER = "2.0"

  implicit val myExceptionHandler = ExceptionHandler {
    case e: ErrorException =>
      replyWithError(e.error.code.value, Some(e.error.message))("", None)

    case e: Throwable =>
      replyWithError(-32603, Some(e.getMessage))("", None)
  }

  val routes: Route = {
    pathPrefix(endpoint) {
      path("loopring") {
        post {
          entity(as[JsonRpcRequest]) { jsonReq =>
            implicit val method = jsonReq.method
            implicit val id = jsonReq.id

            if (id.isEmpty) {
              replyWithError(-32000, Some("`id missing"))
            } else {
              getPayloadConverter(method) match {
                case None =>
                  replyWithError(-32601)

                case Some(converter) =>
                  jsonReq.params.map(converter.convertToRequest) match {
                    case None =>
                      replyWithError(
                        -32602,
                        Some("`params` is missing, use `{}` as default value")
                      )

                    case Some(req) =>
                      val f = (requestHandler ? req).map {
                        case err: XError => throw ErrorException(err)
                        case other       => other
                      }

                      onSuccess(f) { resp =>
                        replyWith(converter.convertFromResponse(resp))
                      }
                  }
              }
            }
          }
        }
      } ~
        path("ethereum") {
          post {
            entity(as[JsonRpcRequest]) { jsonReq =>
              val f =
                (requestHandler ? XJsonRpcReq(Serialization.write(jsonReq)))
                  .mapTo[XJsonRpcRes]

              onSuccess(f) { resp ⇒
                complete(
                  Serialization.read[JsonRpcResponse](resp.json)
                )
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
