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

package org.loopring.lightcone.actors.support

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.loopring.lightcone.actors.RpcBinding
import org.loopring.lightcone.actors.jsonrpc.{JsonRpcRequest, JsonRpcResponse}
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto.XErrorCode
import scalapb.json4s.JsonFormat
import scala.concurrent.ExecutionContext

trait HttpSupport extends RpcBinding {
  val config: Config
  implicit val materializer: ActorMaterializer

  //todo:for test, not need it
  override val requestHandler: ActorRef = ActorRef.noSender

  def singleRequest(
      req: Any,
      method: String
    )(
      implicit system: ActorSystem,
      ec: ExecutionContext
    ) = {
    val json = req match {
      case m: scalapb.GeneratedMessage => JsonFormat.toJson(m)
    }
    val reqJson = JsonRpcRequest(
      "2.0",
      method,
      Some(json),
      Some("1")
    )
    for {
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            serialization.write(reqJson)
          ),
          uri = Uri(
            s"http://127.0.0.1:${config.getString("jsonrpc.http.port")}/" +
              s"${config.getString("jsonrpc.endpoint")}/${config.getString("jsonrpc.loopring")}"
          )
        )
      )
      res <- response.status match {
        case StatusCodes.OK =>
          response.entity.toStrict(timeout.duration).map { r =>
            val j = parse.parse(r.data.utf8String).extract[JsonRpcResponse]

            j.result match {
              case Some(r1) =>
                getPayloadConverter(method).get
                  .convertToResponse(r1)
              case None =>
                j.error match {
                  case Some(err) =>
                    throw ErrorException(
                      XErrorCode.ERR_INTERNAL_UNKNOWN,
                      s"msg:${err}"
                    )
                  case None =>
                    throw ErrorException(
                      XErrorCode.ERR_INTERNAL_UNKNOWN,
                      s"res:${response}"
                    )
                }
            }
          }
        case _ =>
          throw ErrorException(
            XErrorCode.ERR_INTERNAL_UNKNOWN,
            s"res:${response}"
          )
      }
    } yield res
  }

}
