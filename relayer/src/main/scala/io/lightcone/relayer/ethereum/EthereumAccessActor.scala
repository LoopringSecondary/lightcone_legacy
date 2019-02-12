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

package io.lightcone.relayer.ethereum

import akka.actor._
import akka.cluster.singleton._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.relayer.base._
import io.lightcone.lib._
import io.lightcone.relayer.data._
import io.lightcone.core._
import io.lightcone.relayer.base._
import akka.pattern._
import scala.concurrent._
import scala.util.Random

// Owner: Yadong
object EthereumAccessActor {
  val name = "ethereum_access"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      ma: ActorMaterializer,
      ece: ExecutionContextExecutor,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new EthereumAccessActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      name = EthereumAccessActor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${EthereumAccessActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${EthereumAccessActor.name}_proxy"
    )
  }
}

class EthereumAccessActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends InitializationRetryActor {

  private def monitor = actors.get(EthereumClientMonitor.name)

  var connectionPools: Seq[ActorRef] = HttpConnector
    .connectorNames(config)
    .filter(node => actors.contains(node._1))
    .map {
      case (nodeName, _) => actors.get(nodeName)
    }
    .toSeq

  var nodes: Seq[NodeBlockHeight] = HttpConnector
    .connectorNames(config)
    .map {
      case (nodeName, _) => NodeBlockHeight(nodeName, -1L)
    }
    .toSeq

  override def initialize(): Future[Unit] = {
    if (actors.contains(EthereumClientMonitor.name)) {
      (monitor ? GetNodeBlockHeight.Req())
        .mapAs[GetNodeBlockHeight.Res]
        .map { res =>
          connectionPools = res.nodes
            .filter(_.height > 0)
            .filter(node => actors.contains(node.nodeName))
            .sortWith(_.height > _.height)
            .map(node => actors.get(node.nodeName))
          becomeReady()
        }
    } else {
      Future.failed(
        ErrorException(
          ErrorCode.ERR_ACTOR_NOT_READY,
          "Ethereum client monitor is not ready"
        )
      )
    }
  }

  def ready: Receive = {
    case node: NodeBlockHeight =>
      nodes = nodes.dropWhile(nbh => nbh.nodeName.equals(node.nodeName)) :+ node
      connectionPools = nodes
        .filter(_.height > 0)
        .filter(node => actors.contains(node.nodeName))
        .sortWith(_.height > _.height)
        .map(node => actors.get(node.nodeName))

    case req: JsonRpc.RequestWithHeight =>
      val validPools = nodes.filter(_.height >= req.height)
      if (validPools.nonEmpty) {
        val nodeName = validPools(Random.nextInt(validPools.size)).nodeName
        actors.get(nodeName) forward req.req
      } else {
        sender ! ErrorException(
          code = ErrorCode.ERR_NO_ACCESSIBLE_ETHEREUM_NODE,
          message = "No accessible Ethereum node service"
        )
      }

    case msg: JsonRpc.Request => {
      if (connectionPools.nonEmpty) {
        connectionPools.head forward msg
      } else {
        sender ! ErrorException(
          code = ErrorCode.ERR_NO_ACCESSIBLE_ETHEREUM_NODE,
          message = "No accessible Ethereum node service"
        )
      }
    }

    case msg: ProtoBuf[_] => {
      if (connectionPools.nonEmpty) {
        connectionPools.head forward msg
      } else {
        sender ! ErrorException(
          code = ErrorCode.ERR_NO_ACCESSIBLE_ETHEREUM_NODE,
          message = "No accessible Ethereum node service"
        )
      }
    }
  }
}
