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
import akka.routing._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.loopring.lightcone.proto.actors._

class EthereumConnectionActor(
    settings: XEthereumProxySettings
)(
    implicit
    materilizer: ActorMaterializer,
    timeout: Timeout
) extends Actor
  with ActorLogging {

  private var monitor: ActorRef = _
  private var router: ActorRef = _
  private var connectorGroups: Seq[ActorRef] = Nil
  private var currentSettings: Option[XEthereumProxySettings] = None

  updateSettings(settings)

  def receive: Receive = {
    case settings: XEthereumProxySettings ⇒
      updateSettings(settings)

    case m: XJsonRpcReq ⇒
      // 路由为空 这里是 timeout
      router.forward(m)

    case req: ProtoBuf[_] ⇒
      router.forward(req)

    case msg ⇒
      log.error(s"unsupported request to EthereumConnectionActor: $msg")
  }

  def updateSettings(settings: XEthereumProxySettings) {
    if (router != null) {
      context.stop(router)
    }
    connectorGroups.foreach(context.stop(_))

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

    // 尤其节点的块高度不统一，不能直接做
    // 这里相当于添加了 ActorSelectionRoutee
    router = context.actorOf(
      RoundRobinGroup(connectorGroups.map(_.path.toString).toList).props(),
      "r_ethereum_connector"
    )

    monitor = context.actorOf(
      Props(
        new EthereumClientMonitor(
          router,
          connectorGroups,
          settings.checkIntervalSeconds,
          settings.healthyThreshold
        )
      ),
      "ethereum_connector_monitor"
    )

    currentSettings = Some(settings)
  }
}

