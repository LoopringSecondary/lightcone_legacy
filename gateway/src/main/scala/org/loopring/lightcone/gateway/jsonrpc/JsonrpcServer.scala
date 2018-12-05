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

import com.google.inject.Inject
import com.google.inject.name.Named
import org.json4s.DefaultFormats
import org.loopring.lightcone.gateway.service._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._
import org.json4s.jackson.Serialization
import org.json4s.JsonDSL._
import scala.concurrent.{ ExecutionContext, Future }

class JsonrpcServer @Inject() (@Named("apiService") service: ApiService)(implicit val ec: ExecutionContext) {

  implicit val formats = DefaultFormats

  def handle(json: String): Future[JsonRpcResp] = {
    val rpcReq = parse(json).extract[JsonRpcReq]
    val resp = JsonRpcResp(id = rpcReq.id, jsonrpc = rpcReq.jsonrpc)

    val resAny = service.handle(rpcReq)

    //todo:需要充分测试json4s
    resAny match {
      case resFuture: Future[Any] ⇒
        resFuture map {
          res ⇒
            resp.copy(result = Some(res.toString))
        }
      case resNoFuture: Any ⇒
        Future.successful(
          resp.copy(result = Some(resNoFuture.toString))
        )
    }

  }
}
