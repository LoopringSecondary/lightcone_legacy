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

object EthereumAccessActor {
  val name = "ethereum_access"

  def startSingleton(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      ma: ActorMaterializer,
      ece: ExecutionContextExecutor
    ): ActorRef = {
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new EthereumAccessActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(name)
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

// TODO(yadong): 是否可以替代ActorSelection
class EthereumAccessActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val ma: ActorMaterializer,
    val ece: ExecutionContextExecutor)
    extends Actor
    with Stash
    with ActorLogging {

  private def monitor: ActorRef = actors.get(EthereumClientMonitor.name)
  var connectionPools: Seq[(ActorSelection, Long)] = Nil

  override def preStart() = {
    val fu = (monitor ? GetNodeBlockHeight.Req())
      .mapAs[GetNodeBlockHeight.Res]
    fu onComplete {
      case Success(res) =>
        connectionPools = res.nodes.map(
          node =>
            context
              .actorSelection(node.path) -> node.height
        )
        self ! Notify("initialized")
      case Failure(e) =>
        log.error(s"failed to start EthereumAccessActor: ${e.getMessage}")
        context.stop(self)
    }
  }

  override def receive: Receive = initialReceive

  def initialReceive: Receive = {
    case Notify("initialized", _) =>
      unstashAll()
      context.become(normalReceive)
    case _: NodeBlockHeight =>
    case _ =>
      stash()
  }

  def normalReceive: Receive = {
    case node: NodeBlockHeight =>
      connectionPools = (connectionPools.toMap +
        (context.actorSelection(node.path) -> node.height)).toSeq
        .filter(_._2 >= 0)
        .sortWith(_._2 > _._2)

    case req: JsonRpc.RequestWithHeight =>
      val validPools = connectionPools.filter(_._2 > req.height)
      if (validPools.nonEmpty) {
        validPools(Random.nextInt(validPools.size))._1 forward req.req
      } else {
        sender ! ErrorException(
          code = ErrorCode.ERR_NO_ACCESSIBLE_ETHEREUM_NODE,
          message = "No accessible Ethereum node service"
        )
      }

    case msg: JsonRpc.Request => {
      if (connectionPools.nonEmpty) {
        connectionPools.head._1 forward msg
      } else {
        sender ! ErrorException(
          code = ErrorCode.ERR_NO_ACCESSIBLE_ETHEREUM_NODE,
          message = "No accessible Ethereum node service"
        )
      }
    }

    case msg: ProtoBuf[_] => {
      if (connectionPools.nonEmpty) {
        connectionPools.head._1 forward msg
      } else {
        sender ! ErrorException(
          code = ErrorCode.ERR_NO_ACCESSIBLE_ETHEREUM_NODE,
          message = "No accessible Ethereum node service"
        )
      }
    }
  }
}
