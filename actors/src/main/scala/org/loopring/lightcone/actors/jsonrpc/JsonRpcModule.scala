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
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core.data._
import org.json4s._
import org.json4s.JsonAST.JValue
import akka.actor._
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.pattern.ask
import com.typesafe.config.Config
import org.json4s.jackson.Serialization
import com.typesafe.config.Config
import scala.reflect.runtime.universe._
import scala.concurrent.duration._
import scala.concurrent._
import scala.util.{Failure, Success}

// Owner: Daniel
trait JsonRpcModule extends JsonRpcBinding with JsonSupport {
  val requestHandler: ActorRef
  val config: Config

  implicit val system: ActorSystem
  implicit val timeout: Timeout
  implicit val ec: ExecutionContext

  val JSON_RPC_VER = "2.0"

  val myExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: ErrorException =>
      replyWithError(e.error.code.value, Some(e.getMessage()))(None)

    case e: Throwable =>
      replyWithError(-32603, Some(e.getMessage))(None)
  }

  val routes: Route = handleExceptions(myExceptionHandler) {
    pathPrefix(config.getString("jsonrpc.endpoint")) {
      path(config.getString("jsonrpc.loopring")) {
        post {
          entity(as[JsonRpcRequest]) { jsonReq =>
            val method = jsonReq.method
            implicit val id = jsonReq.id

            if (id.isEmpty) {
              replyWithError(-32000, Some("`id missing"))
            } else {
              getReply(method) match {
                case None =>
                  replyWithError(
                    -32601,
                    Some(s"The method `${method}` could not be found.")
                  )

                case Some(converter) =>
                  jsonReq.params.map(converter.jsonToInternalRequest) match {
                    case None =>
                      replyWithError(
                        -32602,
                        Some("`params` is missing, use `{}` as default value")
                      )

                    case Some(req) =>
                      val f = (requestHandler ? req).map {
                        case err: Error => throw ErrorException(err)
                        case other      => other
                      }

                      onSuccess(f) { resp =>
                        replyWith(converter.internalResponseToJson(resp))
                      }
                  }
              }
            }
          }
        }
      } ~
        path(config.getString("jsonrpc.ethereum")) {
          post {
            entity(as[JsonRpcRequest]) { jsonReq =>
              val f =
                (requestHandler ? JsonRpc.Request(Serialization.write(jsonReq)))
                  .mapTo[JsonRpc.Response]

              onComplete(f) {
                case Success(resp) =>
                  complete(Serialization.read[JsonRpcResponse](resp.json))
                case Failure(e) =>
                  replyWithError(-32603, Some(e.getMessage))(jsonReq.id)
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
      implicit
      id: Option[String]
    ) =
    complete(
      JsonRpcResponse(
        JSON_RPC_VER,
        None,
        Some(JsonRpcError(code, message, data)),
        id
      )
    )

  private def replyWith(content: JValue)(implicit id: Option[String]) =
    complete(JsonRpcResponse(JSON_RPC_VER, Option(content), None, id))

}
