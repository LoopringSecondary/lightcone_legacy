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
import org.loopring.lightcone.ethereum.data.{Address => LAddress}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import akka.pattern._

import scala.io.StdIn
import java.io.File

import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.loopring.lightcone.actors.core.OrderbookManagerActor
import org.loopring.lightcone.actors.ethereum.HttpConnector
import org.loopring.lightcone.core.base.TokenManager
import org.loopring.lightcone.proto._

import scala.concurrent.Await
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
    "akka.cluster.roles"
  )

  configItems foreach { i =>
    log.info(s"--> $i = ${config.getValue(i)}")
  }

  sys.ShutdownHookThread { terminiate() }

  val injector = Guice.createInjector(new CoreModule(config))
  val system = injector.instance[ActorSystem]

  val connectionPools = (config
    .getConfigList("ethereum_client_monitor.nodes")
    .asScala
    .zipWithIndex
    .map {
      case (c, index) =>
        val node = EthereumProxySettings
          .Node(host = c.getString("host"), port = c.getInt("port"))
        val nodeName = s"ethereum_connector_http_$index"
        val props =
          Props(new HttpConnector(node)(ActorMaterializer()(system)))
        system.actorOf(props, nodeName)
    })
    .toSeq

  val blockNumJsonRpcReq = JsonRpc.Request(
    "{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\",\"params\":[],\"id\":64}"
  )
  var inited = false
  implicit val timeout = Timeout(5 second)
  while (!inited) {
    val f =
      (connectionPools(0) ? blockNumJsonRpcReq).mapTo[JsonRpc.Response]
    val r = Await.result(f, timeout.duration)
    if ("" != r.json) {
      inited = true
    }
  }

  val tokenManager = injector.instance[TokenManager]

  val WETH_TOKEN = TokenMetadata(
    address = LAddress("0x7Cb592d18d0c49751bA5fce76C1aEc5bDD8941Fc").toString,
    decimals = 18,
    burnRate = 0.4,
    symbol = "WETH",
    usdPrice = 1000
  )

  val LRC_TOKEN = TokenMetadata(
    address = LAddress("0x97241525fe425C90eBe5A41127816dcFA5954b06").toString,
    decimals = 18,
    burnRate = 0.4,
    symbol = "LRC",
    usdPrice = 1000
  )

  val GTO_TOKEN = TokenMetadata(
    address = LAddress("0x2D7233F72AF7a600a8EbdfA85558C047c1C8F795").toString,
    decimals = 18,
    burnRate = 0.4,
    symbol = "GTO",
    usdPrice = 1000
  )

  tokenManager.addToken(WETH_TOKEN)
  tokenManager.addToken(LRC_TOKEN)
  tokenManager.addToken(GTO_TOKEN)

  println(
    s"### main tokenmanager ${tokenManager.hashCode()}, ${LAddress("0x2D7233F72AF7a600a8EbdfA85558C047c1C8F795").toString},  ${tokenManager
      .getToken("0x97241525fe425c90ebe5a41127816dcfa5954b06")}"
  )

  injector.instance[CoreDeployer].deploy(connectionPools)

  Thread.sleep(3000)
  val actors = injector.instance[Lookup[ActorRef]]
  config
    .getObjectList("markets")
    .asScala
    .map { item =>
      val c = item.toConfig
      val marketId = MarketId(
        LAddress(c.getString("priamry")).toString,
        LAddress(c.getString("secondary")).toString
      )
      val orderBookInit = GetOrderbook.Req(0, 100, Some(marketId))
      val orderBookInitF = actors.get(OrderbookManagerActor.name) ? orderBookInit
      Await.result(orderBookInitF, timeout.duration)
    }

  println(s"type `stopstopstop` to terminate")

  while ("stopstopstop" != StdIn.readLine()) {}

  terminiate()

  private def terminiate() = {
    Try(system.terminate())
    Try(Kamon.stopAllReporters())
  }

}
