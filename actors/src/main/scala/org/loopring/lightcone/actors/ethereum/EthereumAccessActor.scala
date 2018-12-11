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

package org.loopring.lightcone.actors.ethereum

import akka.actor._
import akka.cluster.sharding._
import akka.stream.ActorMaterializer
import akka.event.LoggingReceive
import akka.routing.RoundRobinPool
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core.XOrderStatus._
import org.loopring.lightcone.proto.core._
import scala.concurrent._

object EthereumAccessActor extends EventlySharded {
  val numOfShards = 2
  val entitiesPerShard = 1
  val name = "ethereum_access"

  def startShardRegion()(
    implicit
    system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef],
    ma: ActorMaterializer,
    ece: ExecutionContextExecutor
  ): ActorRef = {
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new EthereumAccessActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }
}

class EthereumAccessActor()(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val ma: ActorMaterializer,
    val ece: ExecutionContextExecutor
) extends Actor
  with ActorLogging {

  val conf = config.getConfig(EthereumAccessActor.name)
  val thisConfig = try {
    conf.getConfig(self.path.name).withFallback(conf)
  } catch {
    case e: Throwable ⇒ conf
  }
  log.info(s"config for ${self.path.name} = $thisConfig")

  val settings = XEthereumProxySettings(
    poolSize = thisConfig.getInt("pool-size"),
    checkIntervalSeconds = thisConfig.getInt("check-interval-seconds"),
    healthyThreshold = thisConfig.getDouble("healthy-threshold").toFloat,
    nodes = thisConfig.getList("nodes").toArray.map { v ⇒
      val c = v.asInstanceOf[Config]
      XEthereumProxySettings.XNode(
        host = c.getString("host"),
        port = c.getInt("port"),
        ipcPath = c.getString("ipc-path")
      )
    }
  )

  private var monitor: ActorRef = _
  private var router: ActorRef = _
  private var connectorGroups: Seq[ActorRef] = Nil
  private var currentSettings: Option[XEthereumProxySettings] = None

  updateSettings(settings)

  def receive: Receive = {
    case settings: XEthereumProxySettings ⇒
      updateSettings(settings)

    case req ⇒
      router.forward(req)
  }

  def updateSettings(settings: XEthereumProxySettings) {
    if (router != null) {
      context.stop(router)
    }
    connectorGroups.foreach(context.stop)

    connectorGroups = settings.nodes.zipWithIndex.map {
      case (node, index) ⇒
        val ipc = node.ipcPath.nonEmpty

        val nodeName =
          if (ipc) s"ethereum_connector_ipc_$index"
          else s"ethereum_connector_http_$index"

        val props =
          if (ipc) Props(new IpcConnector(node))
          else Props(new HttpConnector(node))

        context.actorOf(
          RoundRobinPool(
            settings.poolSize
          ).props(props),
          nodeName
        )
    }

    router = context.actorOf(
      Props(new EthereumServiceRouter()),
      "r_ethereum_connector"
    )

    monitor = context.actorOf(
      Props(
        new EthereumClientMonitor(
          router,
          connectorGroups,
          settings.checkIntervalSeconds
        )
      ),
      "ethereum_connector_monitor"
    )
    currentSettings = Some(settings)
  }
}
