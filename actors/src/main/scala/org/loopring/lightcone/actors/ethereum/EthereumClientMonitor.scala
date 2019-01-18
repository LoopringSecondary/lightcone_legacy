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
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import org.json4s.DefaultFormats
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

import scala.concurrent._
import scala.util._

// Owner: Yadong
object EthereumClientMonitor {
  val name = "ethereum_client_monitor"

  def start(
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
        singletonProps = Props(new EthereumClientMonitor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      name = EthereumClientMonitor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${EthereumClientMonitor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${EthereumClientMonitor.name}_proxy"
    )
  }
}

class EthereumClientMonitor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends ActorWithPathBasedConfig(EthereumClientMonitor.name)
    with RepeatedJobActor {

  def ethereumAccessor = actors.get(EthereumAccessActor.name)

  var nodes: Map[String, NodeBlockHeight] =
    HttpConnector.connectorNames(config).map {
      case (nodeName, _) => nodeName -> NodeBlockHeight(nodeName, -1L)
    }

  val checkIntervalSeconds: Int = selfConfig.getInt("check-interval-seconds")

  override val repeatedJobs: Seq[Job] = Seq(
    Job(
      name = EthereumClientMonitor.name,
      dalayInSeconds = checkIntervalSeconds,
      run = () => checkNodeHeight,
      initialDalayInSeconds = checkIntervalSeconds
    )
  )

  override def initialize(): Future[Unit] = {
    checkNodeHeight.map(_ => becomeReady())
  }

  def ready: Receive = super.receiveRepeatdJobs orElse {
    case _: GetNodeBlockHeight.Req =>
      sender ! GetNodeBlockHeight.Res(
        nodes.map(_._2).toSeq
      )
  }

  def checkNodeHeight = {
    log.debug("start scheduler check highest block...")
    Future.sequence(nodes.map {
      case (nodeName, nodeBlockHeight) =>
        if (actors.contains(nodeName)) {
          val actor = actors.get(nodeName)
          for {
            blockNumResp: Long <- (actor ? GetBlockNumber.Req())
              .mapAs[GetBlockNumber.Res]
              .map(_.result)
              .map(anyHexToLong)
              .recover {
                case e: Exception =>
                  log
                    .error(
                      s"exception on getting blockNumber: ${actor}: ${e.getMessage}"
                    )
                  -1L
              }
          } yield {
            val nodeBlockHeight =
              NodeBlockHeight(nodeName = nodeName, height = blockNumResp)
            nodes = nodes + (nodeName -> nodeBlockHeight)
            ethereumAccessor ! nodeBlockHeight
          }
        } else {
          Future.successful(Unit)
        }
    })
  }

  def anyHexToLong: PartialFunction[Any, Long] = {
    case s: String => Numeric.toBigInt(s).longValue()
    case _         => -1L
  }
}
