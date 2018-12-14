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

package org.loopring.lightcone.gateway

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import akka.pattern.ask

import scala.reflect.ClassTag

import org.loopring.lightcone.proto._
import scala.io.StdIn

import akka.actor.ActorRef

trait RouteSupport extends JsonSupport {
  val requestHandler: ActorRef
  implicit val timeout: Timeout

  def handle[RESP](req: Any)(implicit tag: ClassTag[RESP]) =
    handlerInternal(req)

  def handle[RESP, REQ](
      implicit tagRESP: ClassTag[RESP],
      um: akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller[REQ]
    ) = entity(as[REQ])(handlerInternal)

  private def handlerInternal[RESP, REQ](
      req: REQ
    )(
      implicit tagRESP: ClassTag[RESP]
    ) = onSuccess(requestHandler ? req) {
    case resp: RESP =>
      complete(StatusCodes.OK, resp)
    case err: XError =>
      complete(StatusCodes.InternalServerError, err)
    case e: String =>
      complete(StatusCodes.InternalServerError, XError(message = e))
    case e =>
      complete(
        StatusCodes.InternalServerError,
        XError(message = s"unexpected response of type ${e.getClass.getName}")
      )

  }
}
