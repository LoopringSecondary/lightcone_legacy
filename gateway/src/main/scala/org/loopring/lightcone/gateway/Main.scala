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

import org.loopring.lightcone.gateway.jsonrpc._
import org.loopring.lightcone.proto._
import com.google.inject.Guice
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.loopring.lightcone.actors.entrypoint.EntryPointActor
import org.loopring.lightcone.actors.base.Lookup
import org.slf4s.Logging
import net.codingwell.scalaguice.InjectorExtensions._
import akka.actor._
import akka.util.Timeout
import scala.io.StdIn

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.HttpApp

object Main extends HttpApp with JsonRpcModule with Logging {
  // val configPathOpt = Option(System.getenv("LIGHTCONE_CONFIG_PATH")).map(_.trim)
  // val injector = ClusterDeployer.deploy(configPathOpt)
  // val system = injector.instance[ActorSystem]
  // val actors = injector.instance[Lookup[ActorRef]]
  // actors.get(EntryPointActor.name)

  // println(s"Hit RETURN to terminate")

  // StdIn.readLine()

  // //Shutdown
  // system.terminate()

  implicit val system = ActorSystem("Lightcone", ConfigFactory.load())

  val requestHandler = system.actorOf(Props(new Actor() {

    def receive = {
      case x => sender ! x
    }
  }))

  bindRequest[XRawOrder].toResponse[XRawOrder]("abc")

  override val routes = jsonRPCRoutes

  def main(args: Array[String]) {
    startServer("localhost", 8080, system)
  }
}
