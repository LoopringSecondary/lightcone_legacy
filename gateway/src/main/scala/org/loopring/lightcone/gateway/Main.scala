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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask

import scala.io.StdIn

import akka.actor._

import de.heikoseeberger.akkahttpjson4s.{Json4sSupport => J4s}

object Main extends MainRoute {

  val host = "localhost"
  val port = 8080
  implicit val timeout = Timeout(20.seconds)
  implicit val system = ActorSystem("simple-rest-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val requestHandler: ActorRef = system.actorOf(Props(new Actor {

    def receive = {
      case x: Any => sender ! x
    }
  }))

  def main(args: Array[String]): Unit = {

    //Startup, and listen for requests
    val bindingFuture = Http().bindAndHandle(route, host, port)
    println(
      s"Waiting for requests at http://$host:$port/...\nHit RETURN to terminate"
    )

    StdIn.readLine()
    StdIn.readLine()
    StdIn.readLine()

    //Shutdown
    bindingFuture.flatMap(_.unbind())
    system.terminate()
  }
}
