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

package io.lightcone.relayer.integration
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import io.lightcone.core.{ErrorCode, ErrorException}
import io.lightcone.lib.ProtoSerializer
import io.lightcone.relayer.jsonrpc.{JsonRpcRequest, JsonRpcResponse}
import org.scalatest.Matchers
import org.scalatest.matchers.Matcher
import org.slf4s.Logging
import scalapb.GeneratedMessage

import scala.concurrent._

trait HttpHelper extends RpcBindingForTest with Logging {
  helper: Matchers =>

  val ps = new ProtoSerializer

  implicit class RichRequest[T <: GeneratedMessage](req: T) {

    def expectUntil[R](
        matcher: Matcher[R],
        expectTimeout: Timeout = timeout
      ) = {
      var resOpt: Option[R] = None
      var resMatched = false
      val lastTime = System
        .currentTimeMillis() + expectTimeout.duration.toMillis
      while (!resMatched &&
             System.currentTimeMillis() <= lastTime) {
        val res = Await.result(req.request, timeout.duration).asInstanceOf[R]
        resOpt = Some(res)
        resMatched = matcher(res).matches
        if (!resMatched) {
          Thread.sleep(200)
        }
      }
      if (resOpt.isEmpty || !resMatched) {
        throw new Exception(
          s"Timed out waiting for result of req:${req} "
        )
      } else {
        //最好判断，便于返回未匹配的信息
        resOpt.get should matcher
      }
    }

    def expect[R <: GeneratedMessage](matcher: Matcher[R]) = {
      Await.result(req.request, timeout.duration).asInstanceOf[R] should matcher
    }

    def request(
        implicit
        system: ActorSystem,
        ec: ExecutionContext
      ): Future[Any] = {
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      val method = methods(req.getClass)
      val reqJson = JsonRpcRequest("2.0", method, ps.serialize(req), Some("1"))
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
                  getReply(method).get
                    .jsonToExternalResponse(r1)
                case None =>
                  j.error match {
                    case Some(err) =>
                      throw ErrorException(
                        ErrorCode.ERR_INTERNAL_UNKNOWN,
                        s"msg:${err}"
                      )
                    case None =>
                      throw ErrorException(
                        ErrorCode.ERR_INTERNAL_UNKNOWN,
                        s"res:${response}"
                      )
                  }
              }
            }
          case _ =>
            throw ErrorException(
              ErrorCode.ERR_INTERNAL_UNKNOWN,
              s"res:${response}"
            )
        }
      } yield res
    }
  }

}
