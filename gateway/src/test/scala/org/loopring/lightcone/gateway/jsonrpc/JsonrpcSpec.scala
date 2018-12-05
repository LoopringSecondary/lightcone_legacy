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

import akka.actor.Actor
import akka.util.Timeout
import org.loopring.lightcone.gateway.service._
import org.scalatest.{ FlatSpec, Matchers }

import scala.concurrent._
import scala.concurrent.duration._

class JsonrpcSpec extends FlatSpec with Matchers {

  "jsonrpc" should "handle the message" in {
    implicit val timeout = Timeout(3 second)
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
    val service = new ApiService(Actor.noSender)
    val req = JsonRpcReq(1, "test", "2.0", None)
    val jsonrpc = new JsonrpcServer(service)
    val res = jsonrpc.handle("{\"jsonrpc\": \"2.0\", \"method\": \"test\", \"id\": 1}")
    res match {
      case resFuture: Future[Any] ⇒
        val r = Await.result(resFuture.mapTo[Any], timeout.duration)
        r match {
          case s: String ⇒
            info(s"#### $r")
          case resp: JsonRpcResp ⇒
            info(s"#### $resp")
            resp.result should be("Some(test)")
        }
    }
  }
}
