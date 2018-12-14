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

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.google.inject.Guice
import com.typesafe.config.Config
import net.codingwell.scalaguice.InjectorExtensions._
import org.loopring.lightcone.actors._
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.entrypoint.EntryPointActor
import org.slf4s.Logging
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.StdIn

object Maim extends App with MainRoute with Logging {

  val configPathOpt = Option(System.getenv("LIGHTCONE_CONFIG_PATH")).map(_.trim)
  val injector = ClusterDeployer.deploy(configPathOpt)

  implicit val config = injector.instance[Config]
  implicit val system = injector.instance[ActorSystem]
  implicit val materializer = injector.instance[ActorMaterializer]
  implicit val ec = injector.instance[ExecutionContext]
  implicit val timeout = config.getInt("restful.timeout").seconds

  val actors = injector.instance[Lookup[ActorRef]]
  val requestHandler = actors.get(EntryPointActor.name)

  val host = config.getString("restful.host")
  val port = config.getInt("restful.port")

  val binding = Http().bindAndHandle(route, host, port)

  log.info(
    s"Waiting for requests at http://$host:$port/...\nHit RETURN to terminate"
  )

  StdIn.readLine()

  //Shutdown
  binding.flatMap(_.unbind())
  system.terminate()
}
