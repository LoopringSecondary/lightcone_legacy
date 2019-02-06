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

import java.io.File

import akka.actor._
import com.google.inject.Guice
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import net.codingwell.scalaguice.InjectorExtensions._
import org.slf4s.Logging
import scala.io.StdIn
import scala.util.Try

// Owner: Daniel
object Main extends App with Logging {
  Kamon.loadReportersFromConfig()

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
    "akka.cluster.roles")
  log.debug(s"----> config = ${config}")

  configItems foreach { i =>
    log.info(s"--> $i = ${config.getValue(i)}")
  }

  sys.ShutdownHookThread { terminiate() }

  val injector = Guice.createInjector(new CoreModule(config))
  val system = injector.instance[ActorSystem]
  val dbManager = injector.instance[DatabaseConfigManager]

  injector.instance[CoreDeployer].deploy()

  println(s"type `stopstopstop` to terminate")

  while ("stopstopstop" != StdIn.readLine()) {}

  terminiate()

  private def terminiate() = {
    Try(system.terminate())
    Try(dbManager.close())
    Try(Kamon.stopAllReporters())
  }

}
