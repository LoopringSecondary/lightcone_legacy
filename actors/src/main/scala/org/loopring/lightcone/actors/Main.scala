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

package org.loopring.lightcone.actors

import com.google.inject.Guice
import com.typesafe.config.ConfigFactory
import org.loopring.lightcone.actors.entrypoint.EntryPointActor
import org.loopring.lightcone.actors.base.Lookup
import org.slf4s.Logging
import net.codingwell.scalaguice.InjectorExtensions._
import akka.actor._
import scala.io.StdIn
import java.io.File

object Main extends App with Logging {

  val configPathOpt = Option(System.getenv("LIGHTCONE_CONFIG_PATH")).map(_.trim)

  log.info(s"--> config_path = ${configPathOpt}")

  val baseConfig = ConfigFactory.load()

  val config = configPathOpt match {
    case Some(path) if path.nonEmpty =>
      ConfigFactory.parseFile(new File(path)).withFallback(baseConfig)
    case _ =>
      baseConfig
  }

  val configItems = Seq(
    "akka.remote.netty.tcp.hostname",
    "akka.remote.netty.tcp.port",
    "akka.cluster.seed-nodes",
    "akka.cluster.roles"
  )

  configItems foreach { i =>
    log.info(s"--> $i = ${config.getString(i)}")
  }

  sys.ShutdownHookThread { system.terminate() }

  val injector = Guice.createInjector(new CoreModule(config))
  val system = injector.instance[ActorSystem]

  injector.instance[ClusterDeployer].deploy()

  println(s"Hit RETURN to terminate")

  StdIn.readLine()

  //Shutdown
  system.terminate()

}
