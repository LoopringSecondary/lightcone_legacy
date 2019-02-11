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

package io.lightcone.relayer

import io.lightcone.core._
import akka.actor.ActorRef
import akka.pattern.ask
import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest._

trait IntegrationTestHelper extends testing.OrderHelper with Matchers {
  implicit val timeout = akka.util.Timeout(10 seconds)
  def entrypoint: ActorRef

  implicit class Rich_ActorRef(actor: ActorRef) {

    def ??[T](msg: Any): T = {
      Await.result(actor ? msg, timeout.duration).asInstanceOf[T]
    }
  }

  def send(req: Any) = Request(req)

  def testRpc(req: => Any)(res: => Any) = send(req).expects(res)

  case class Request(req: Any) {

    def expects(res: Any) = {
      Await.result(entrypoint ? req, timeout.duration) should be(res)
    }
  }

  // TODO(hongyu)
  def killActors(name: String) = {}

}
