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
import akka.stream.ActorMaterializer
import com.google.inject.Guice
import com.typesafe.config.ConfigFactory
import net.codingwell.scalaguice.InjectorExtensions._
import org.loopring.lightcone.gateway.api.HttpAndIOServer
import org.loopring.lightcone.gateway.api.service._
import org.loopring.lightcone.gateway.jsonrpc._
import org.loopring.lightcone.gateway.socketio._
import org.loopring.lightcone.gateway.inject._

object Main extends App {

  // QUESTION(dongw):
  // 这里的port和ioPort是akka的，还是jsonrpc的？
  case class AppSettings(
      port: Int = 9277,
      ioPort: Int = 9278,
      seeds: Seq[String] = Seq.empty
  )

  val systemName = "lightcone_gateway"
  val systemPrefix = s"akka.tcp://${systemName}@"

  new scopt.OptionParser[AppSettings](systemName) {
    head("systemName")

    opt[Int]('p', "port").action((x, c) ⇒ {
      c.copy(port = x)
    }).text("http server port")

    opt[Int]('i', "ioserver").action((x, c) ⇒ {
      c.copy(port = x)
    }).text("socketio server port")

    opt[Seq[String]]('s', "seeds").action((x, c) ⇒ {
      c.copy(seeds = x)
    }).text("cluster seeds")

  }.parse(args, AppSettings()) match {
    case None ⇒ println("Unable to parse args")
    case Some(settings) ⇒

      val seeds = settings.seeds
        .map(systemPrefix + _.trim)
        .mkString("\"", "\",\"", "\"")

      // TODO(Toan) 这里的配置还没有测试
      // QUESTION(dongw): should we change akka port, hostname, and seed-nodes?
      val config = ConfigFactory
        .parseString(
          s"""
             |akka.remote.netty.tcp.port=0
             |akka.remote.netty.tcp.hostname="127.0.0.1"
             |akka.cluster.seed-nodes=["akka.tcp://Lightcone@127.0.0.1:2555"]
             |jsonrpc.http.port=${settings.port}
             |jsonrpc.socketio.port=${settings.ioPort}
           """.stripMargin
        )
        .withFallback(ConfigFactory.load())

      val injector = Guice.createInjector(CoreModule(config))
      injector.instance[HttpAndIOServer]
  }
}
