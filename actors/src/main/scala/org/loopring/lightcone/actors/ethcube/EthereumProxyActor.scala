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

package org.loopring.lightcone.actors.ethcube

import akka.actor._
import akka.routing._
import akka.stream.ActorMaterializer
import org.loopring.lightcone.proto.ethrpc._

class EthereumProxyActor(settings: EthereumProxySettings)(
    implicit
    materilizer: ActorMaterializer
) extends Actor
  with ActorLogging {

  private val connectorGroups: Seq[ActorRef] = settings.nodes.zipWithIndex.map {
    case (node, index) ⇒
      val props =
        if (node.ipcPath.nonEmpty) Props(new IpcConnector(node))
        else Props(new HttpConnector(node))

      context.actorOf(
        RoundRobinPool(settings.poolSize).props(props),
        s"connector_group_$index"
      )
  }

  // 这里相当于添加了 ActorSelectionRoutee
  private val requestRouterActor = context.actorOf(
    RoundRobinGroup(connectorGroups.map(_.path.toString).toList).props(),
    "request_router_actor"
  )

  private val manager = context.actorOf(
    Props(
      new ConnectionManager(
        requestRouterActor,
        connectorGroups,
        settings.checkIntervalSeconds,
        settings.healthyThreshold
      )
    ),
    "ethereum_connector_manager"
  )

  def receive: Receive = {
    case m: JsonRpcReq ⇒
      // 路由为空 这里是 timeout
      requestRouterActor.forward(m)
    case req: ProtoBuf[_] ⇒
      requestRouterActor.forward(req)
  }
}

