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

package org.loopring.lightcone.actors.support

import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.routing.RoundRobinPool
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.proto.{EthereumProxySettings, JsonRpc}

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.util.Random
import akka.pattern._
import com.dimafeng.testcontainers.GenericContainer
import com.typesafe.config.ConfigFactory
import org.junit.runner.Description
import org.rnorth.ducttape.TimeoutException
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.containers.ContainerLaunchException
import org.testcontainers.containers.wait.strategy.Wait
import akka.pattern._
import org.loopring.lightcone.actors.jsonrpc.JsonSupport

import scala.concurrent.duration._

//object EthereumDocker {
//
//  implicit private val suiteDescription =
//    Description.createSuiteDescription(this.getClass)
//
//  val ethContainer = GenericContainer(
//    "kongliangzhong/loopring-ganache:v2",
//    exposedPorts = Seq(8545),
//    waitStrategy = Wait.forListeningPort()
//  )
//
//  ethContainer.starting()
//}

trait EthereumSupport {
  my: CommonSpec =>

  implicit val requestBuilder = new EthereumCallRequestBuilder
  implicit val batchRequestBuilder = new EthereumBatchCallRequestBuilder

  actors.add(EthereumQueryActor.name, EthereumQueryActor.start)
  actors.add(
    EthereumQueryMessageValidator.name,
    MessageValidationActor(
      new EthereumQueryMessageValidator(),
      EthereumQueryActor.name,
      EthereumQueryMessageValidator.name
    )
  )

  if (!actors.contains(GasPriceActor.name)) {
    actors.add(GasPriceActor.name, GasPriceActor.start)
  }

  val poolSize =
    config.getConfig(EthereumClientMonitor.name).getInt("pool-size")

  val nodesConfig = ConfigFactory
    .parseString(ethNodesConfigStr)
    .getConfigList("nodes")
    .asScala
    .map { c =>
      EthereumProxySettings
        .Node(host = c.getString("host"), port = c.getInt("port"))
    }

  val connectionPools = (nodesConfig.zipWithIndex.map {
    case (node, index) =>
      val nodeName = s"ethereum_connector_http_$index"
      val props =
        Props(new HttpConnector(node))
      system.actorOf(props, nodeName)
  }).toSeq

  val blockNumJsonRpcReq = JsonRpc.Request(
    "{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\",\"params\":[],\"id\":64}"
  )

  //必须等待connectionPools启动完毕才能启动monitor和accessActor
  try Unreliables.retryUntilTrue(
    10,
    TimeUnit.SECONDS,
    () => {
      val f =
        (connectionPools(0) ? blockNumJsonRpcReq).mapTo[JsonRpc.Response]
      val res = Await.result(f, timeout.duration)
      res.json != ""
    }
  )
  catch {
    case e: TimeoutException =>
      throw new ContainerLaunchException(
        "Timed out waiting for container port to open mysqlContainer should be listening)"
      )
  }

  actors.add(
    EthereumClientMonitor.name,
    EthereumClientMonitor.start(connectionPools)
  )
  actors.add(EthereumAccessActor.name, EthereumAccessActor.start)

}
