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

package org.loopring.lightcone.gateway.service

import akka.actor.Actor
import akka.util.Timeout
import org.loopring.lightcone.gateway.jsonrpc.JsonrpcServer
import org.loopring.lightcone.gateway_bak.jsonrpc.JsonRpcServer
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent._
import scala.concurrent.duration._

class ApiServiceSpec extends FlatSpec with Matchers {

  "apiservice" should "handle the message" in {
    implicit val timeout = Timeout(3 second)
    val service = new ApiService(Actor.noSender)
    val req = JsonRpcReq(1, "test", "2.0", None)
    val res = service.handle(req)
    res match {
      case resFuture: Future[Any] =>
        val r = Await.result(resFuture.mapTo[Any], timeout.duration)
        r match {
          case s: String =>
            info(s"#### $r")
            r should be("test")
        }
    }
  }

}
