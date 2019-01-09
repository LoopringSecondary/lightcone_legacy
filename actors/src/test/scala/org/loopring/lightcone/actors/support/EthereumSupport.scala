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
import scala.concurrent.duration._

trait EthereumSupport {
  my: CommonSpec =>

  implicit val requestBuilder = new EthereumCallRequestBuilder
  implicit val batchRequestBuilder = new EthereumBatchCallRequestBuilder

  actors.add(
    EthereumQueryActor.name,
    EthereumQueryActor.startShardRegion()
  )
  actors.add(
    EthereumQueryMessageValidator.name,
    MessageValidationActor(
      new EthereumQueryMessageValidator(),
      EthereumQueryActor.name,
      EthereumQueryMessageValidator.name
    )
  )

  if (!actors.contains(GasPriceActor.name)) {
    actors.add(GasPriceActor.name, GasPriceActor.startShardRegion())
  }

  val ethMonitorConfig = config.getConfig(EthereumClientMonitor.name)
  val poolSize = ethMonitorConfig.getInt("pool-size")

  val nodesConfig = ethMonitorConfig.getConfigList("nodes").asScala.map { c =>
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

  Thread.sleep(2000) //需要等待HttpConnector初始化，完成1s不足，需要2s
  actors.add(
    EthereumClientMonitor.name,
    EthereumClientMonitor.startSingleton(connectionPools)
  )
  actors.add(EthereumAccessActor.name, EthereumAccessActor.startSingleton())

}
