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

import com.typesafe.config.ConfigFactory
import java.net.InetAddress
import com.google.inject._

object Main {

  case class CmdOptions(
      port: Int = 0,
      managerPort: Int = 8081,
      rpcPort: Int = 8083,
      seeds: Seq[String] = Seq.empty[String],
      roles: Seq[String] = Seq.empty[String],
      configFile: String = ""
  )

  def main(args: Array[String]): Unit = {

    new scopt.OptionParser[CmdOptions]("lightcone") {
      head("Lightcone", "0.1")

      opt[Int]('p', "port")
        .action { (v, options) ⇒
          options.copy(port = v)
        }
        .text("port of this acter system")

      opt[Int]('m', "mport")
        .action { (v, options) ⇒
          options.copy(managerPort = v)
        }
        .text("port of internal rest server [default 8081]")

      opt[Seq[String]]('r', "roles")
        .action { (v, options) ⇒
          options.copy(roles = v)
        }
        .text("cluster seed nodes")

      opt[Seq[String]]('s', "sees")
        .action { (v, options) ⇒
          options.copy(seeds = v)
        }
        .text("node roles")

      opt[String]('c', "config")
        .action { (v, options) ⇒
          options.copy(configFile = v.trim)
        }
        .text("path to configuration file")

    }.parse(args, CmdOptions()) match {
      case None ⇒

      case Some(options) ⇒
        val seedNodes = options.seeds
          .filter(_.nonEmpty)
          .map(s ⇒ s""""akka.tcp://Lightcone@$s"""")
          .mkString("[", ",", "]")

        val roles =
          if (options.roles.isEmpty) "[all]"
          else options.roles
            .filter(_.nonEmpty)
            .mkString("[", ",", "]")

        val hostname = InetAddress.getLocalHost.getHostAddress

        val fallback = if (options.configFile.trim.nonEmpty) {
          ConfigFactory.load(options.configFile.trim)
        } else {
          ConfigFactory.load()
        }

        val config = ConfigFactory
          .parseString(
            s"""
            node-manager.http.port=${options.managerPort}
            akka.remote.netty.tcp.port=${options.port}
            akka.remote.netty.tcp.hostname=$hostname
            akka.cluster.roles=$roles
            akka.cluster.seed-nodes=$seedNodes
            """
          )
          .withFallback(fallback)

        // Deploying NodeManager
        import org.loopring.lightcone.actors.utils.ActorUtil._
        val injector = Guice.createInjector(new CoreModule(config))

        //        injector.getActor("ethereum_access_actor")

        Thread.sleep(2000)
        println("\n\n\n\n============= Akka Node Ready =============\n" +
          "with port: " + options.port + "\n" +
          "with manager-port: " + options.managerPort + "\n" +
          "with rpc-port: " + options.rpcPort + "\n" +
          "with hostname: " + hostname + "\n" +
          "with seeds: " + seedNodes + "\n" +
          "with roles: " + roles + "\n" +
          "============================================\n\n\n\n")

    }
  }
}
