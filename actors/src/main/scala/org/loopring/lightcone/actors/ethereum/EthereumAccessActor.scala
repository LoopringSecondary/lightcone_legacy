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
import akka.cluster.singleton._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._
import akka.pattern._

import scala.concurrent._
import scala.util.{Failure, Random, Success}

// Owner: Yadong
object EthereumAccessActor {
  val name = "ethereum_access"

  def start(
      connectionPools: Seq[ActorRef]
    )(
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
        singletonProps = Props(new EthereumAccessActor(connectionPools)),
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
    connectionPools: Seq[ActorRef]
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val ma: ActorMaterializer,
    val ece: ExecutionContextExecutor)
    extends InitializationRetryActor {

  private def monitor = actors.get(EthereumClientMonitor.name)
  var connectors: Seq[(ActorRef, Long)] = Nil

  override def initialize() = {
    (monitor ? GetNodeBlockHeight.Req())
      .mapAs[GetNodeBlockHeight.Res]
      .map { res =>
        connectors = res.nodes.map(
          node =>
            connectionPools
              .find(_.path.toString.equals(node.path))
              .get -> node.height
        )
        becomeReady()
      }
  }

  def ready: Receive = {
    case node: NodeBlockHeight =>
      connectors = (connectors.toMap +
        (connectionPools
          .find(_.path.toString.equals(node.path))
          .get -> node.height)).toSeq
        .filter(_._2 >= 0)
        .sortWith(_._2 > _._2)
    case req: JsonRpc.RequestWithHeight =>
      val validPools = connectors.filter(_._2 > req.height)
      if (validPools.nonEmpty) {
        validPools(Random.nextInt(validPools.size))._1 forward req.req
      } else {
        sender ! ErrorException(
          code = ErrorCode.ERR_NO_ACCESSIBLE_ETHEREUM_NODE,
          message = "No accessible Ethereum node service"
        )
      }

    case msg: JsonRpc.Request => {
      if (connectors.nonEmpty) {
        connectors.head._1 forward msg
      } else {
        sender ! ErrorException(
          code = ErrorCode.ERR_NO_ACCESSIBLE_ETHEREUM_NODE,
          message = "No accessible Ethereum node service"
        )
      }
    }

    case msg: ProtoBuf[_] => {
      if (connectors.nonEmpty) {
        connectors.head._1 forward msg
      } else {
        sender ! ErrorException(
          code = ErrorCode.ERR_NO_ACCESSIBLE_ETHEREUM_NODE,
          message = "No accessible Ethereum node service"
        )
      }
    }
  }
}
