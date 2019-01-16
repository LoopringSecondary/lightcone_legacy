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
    val name: String = EthereumClientMonitor.name
  )(
    implicit
    system: ActorSystem,
    val config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef],
    ma: ActorMaterializer,
    ece: ExecutionContextExecutor)
    extends Actor
    with Stash
    with ActorLogging
    with RepeatedJobActor
    with NamedBasedConfig {

  implicit val formats = DefaultFormats

  def ethereumAccessor = actors.get(EthereumAccessActor.name)

  def connectionPools = HttpConnector.connectorNames(config).map {
    case (nodeName, _) => actors.get(nodeName)
  }

  var nodes: Map[String, Long] = Map.empty

  val checkIntervalSeconds: Int = selfConfig.getInt("check-interval-seconds")

  override val repeatedJobs: Seq[Job] = Seq(
    Job(
      name = EthereumClientMonitor.name,
      dalayInSeconds = checkIntervalSeconds,
      run = () => checkNodeHeight,
      initialDalayInSeconds = checkIntervalSeconds
    )
  )

  override def preStart(): Unit = {

    checkNodeHeight onComplete {
      case Success(_) =>
        self ! Notify("initialized")
        super.preStart()
      case Failure(e) =>
        log.error(s"Failed to start EthereumClientMonitor:${e.getMessage} ")
        context.stop(self)
    }
  }

  override def receive: Receive = initialReceive

  def initialReceive: Receive = {
    case Notify("initialized", _) =>
      context.become(normalReceive)
      unstashAll()
    case _ =>
      stash()
  }

  def normalReceive: Receive = super.receiveRepeatdJobs orElse {
    case _: GetNodeBlockHeight.Req =>
      sender ! GetNodeBlockHeight.Res(
        nodes.toSeq
          .map(node => NodeBlockHeight(path = node._1, height = node._2))
      )
  }

  def checkNodeHeight = {
    log.debug("start scheduler check highest block...")
    val blockNumJsonRpcReq = JsonRpcReqWrapped(
      id = Random.nextInt(100),
      method = "eth_blockNumber",
      params = None
    )
    import JsonRpcResWrapped._
    Future.sequence(connectionPools.map { g =>
      for {
        blockNumResp: Long <- (g ? blockNumJsonRpcReq.toProto)
          .mapAs[JsonRpc.Response]
          .map(toJsonRpcResWrapped)
          .map(_.result)
          .map(anyHexToLong)
          .recover {
            case e: Exception =>
              log
                .error(s"exception on getting blockNumber: $g: ${e.getMessage}")
              -1L
          }
      } yield {
        nodes = nodes + (g.path.toString -> blockNumResp)
        ethereumAccessor ! NodeBlockHeight(
          path = g.path.toString,
          height = blockNumResp
        )
      }
    })
  }

  def anyHexToLong: PartialFunction[Any, Long] = {
    case s: String => Numeric.toBigInt(s).longValue()
    case _         => -1L
  }
}
