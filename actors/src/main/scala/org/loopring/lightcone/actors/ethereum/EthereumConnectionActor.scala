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
import org.loopring.lightcone.proto.ethrpc._

class EthereumConnectionActor(settings: XEthereumProxySettings)(
    implicit
    materilizer: ActorMaterializer,
    timeout: Timeout
) extends Actor
  with ActorLogging {

  // 块高度检测
  context.actorOf(
    Props(
      new EthereumClientMonitor(
        requestRouterActor,
        connectorGroups,
        settings.checkIntervalSeconds,
        settings.healthyThreshold
      )
    ),
    "ethereum_connector_monitor"
  )

  private val connectorGroups: Seq[ActorRef] = settings.nodes.zipWithIndex.map {
    case (node, index) ⇒
      val ipc = node.ipcPath.nonEmpty
      val nodeName =
        if (ipc) s"ethereum_connector_ipc_$index"
        else s"ethereum_connector_http_$index"

      val props =
        if (ipc) Props(new IpcConnector(node))
        else Props(new HttpConnector(node))

      context.actorOf(RoundRobinPool(settings.poolSize).props(props), nodeName)
  }

  // 这里相当于添加了 ActorSelectionRoutee
  private val requestRouterActor = context.actorOf(
    RoundRobinGroup(connectorGroups.map(_.path.toString).toList).props(),
    "r_ethereum_connector"
  )

  def receive: Receive = {
    case m: XJsonRpcReq ⇒
      // 路由为空 这里是 timeout
      requestRouterActor.forward(m)

    case req: ProtoBuf[_] ⇒
      requestRouterActor.forward(req)

    case msg ⇒
      log.error(s"unsupported request to EthereumConnectionActor: $msg")
  }
}

