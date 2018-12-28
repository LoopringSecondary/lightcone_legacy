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
import akka.routing.RoundRobinPool
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import org.json4s.DefaultFormats
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.lib.TimeProvider
import org.web3j.utils.Numeric

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.util._

object EthereumClientMonitor {
  val name = "ethereum_client_monitor"

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
        singletonProps = Props(new EthereumClientMonitor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
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
    implicit system: ActorSystem,
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

  var connectionPools: Seq[ActorRef] = Nil
  var nodes: Map[String, Int] = Map.empty

  val checkIntervalSeconds: Int = selfConfig.getInt("check-interval-seconds")

  override val repeatedJobs: Seq[Job] = Seq(
    Job(
      name = EthereumClientMonitor.name,
      dalayInSeconds = checkIntervalSeconds,
      run = () ⇒ checkNodeHeight,
      initialDalayInSeconds = checkIntervalSeconds
    )
  )

  override def preStart(): Unit = {
    val poolSize = selfConfig.getInt("pool-size")
    val nodesConfig = selfConfig.getConfigList("nodes").asScala.map { c =>
      EthereumProxySettings
        .Node(host = c.getString("host"), port = c.getInt("port"))
    }
    connectionPools = nodesConfig.zipWithIndex.map {
      case (node, index) =>
        val nodeName = s"ethereum_connector_http_$index"
        val props =
          Props(new HttpConnector(node))
        context.actorOf(RoundRobinPool(poolSize).props(props), nodeName)
    }

    checkNodeHeight onComplete {
      case Success(_) ⇒
        self ! Notify("initialized")
        super.preStart()
      case Failure(e) ⇒
        log.error(s"Failed to start EthereumClientMonitor:${e.getMessage} ")
        context.stop(self)
    }
  }

  override def receive: Receive = initialReceive

  def initialReceive: Receive = {
    case Notify("initialized", _) ⇒
      unstashAll()
      context.become(normalReceive)
    case _ ⇒
      stash()
  }

  def normalReceive: Receive = super.receive orElse {
    case _: GetNodeBlockHeight ⇒
      sender ! GetNodeBlockHeight.Res(
        nodes.toSeq
          .map(node ⇒ NodeBlockHeight(path = node._1, height = node._2))
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
        blockNumResp: Int <- (g ? blockNumJsonRpcReq.toProto)
          .mapAs[XJsonRpcRes]
          .map(toJsonRpcResWrapped)
          .map(_.result)
          .map(anyHexToInt)
          .recover {
            case e: Exception =>
              log
                .error(s"exception on getting blockNumber: $g: ${e.getMessage}")
              -1
          }
      } yield {
        nodes = nodes + (g.path.toString → blockNumResp)
        ethereumAccessor ! NodeBlockHeight(
          path = g.path.toString,
          height = blockNumResp
        )
      }
    })
  }

  def anyHexToInt: PartialFunction[Any, Int] = {
    case s: String ⇒ Numeric.toBigInt(s).intValue()
    case _ ⇒ -1
  }
}
