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

package org.loopring.lightcone.actors.managing

import akka.pattern._
import akka.util.Timeout
import akka.actor._
import akka.cluster._
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import scala.concurrent.duration._
import org.loopring.lightcone.actors.actor._
import akka.cluster.pubsub._
import akka.cluster.pubsub.DistributedPubSubMediator._
import org.loopring.lightcone.proto.deployment._
import org.loopring.lightcone.actors.routing._
import com.google.inject._
import org.loopring.lightcone.actors.actor.OrderManagingActor

@Singleton
class NodeManager(
    injector: Injector,
    config: Config
)(implicit
    cluster: Cluster,
    materializer: ActorMaterializer
)
  extends Actor
  with ActorLogging
  with Timers {

  implicit val system = cluster.system
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(1 seconds)

  Routers.setRouters("cluster_manager", ClusterManager.deploy(injector))

  val http = new NodeHttpServer(config, self)

  val mediator = DistributedPubSub(system).mediator
  mediator ! Subscribe("cluster_manager", self)

  def receive: Receive = {
    case Msg("get_stats") ⇒
      val f = system.actorOf(Props(classOf[LocalActorsDetector])) ? Msg("detect")

      f.mapTo[LocalStats.ActorGroup].map {
        actors ⇒
          LocalStats(cluster.selfRoles.toSeq, Seq(actors))
      }.pipeTo(sender)

    case req: UploadDynamicSettings ⇒
      Routers.clusterManager ! req

    case ProcessDynamicSettings(Some(settings)) ⇒

      Routers.setRouters(
        EthereumAccessActor.name,
        EthereumAccessActor.deploy(injector, settings.ethereumAccessorSettings)
      )

      Routers.setRouters(
        MarketManagingActor.name,
        MarketManagingActor.deploy(injector, settings.orderBookManagerSettingsSeq)
      )

      Routers.setRouters(
        OrderFillHistoryActor.name,
        OrderFillHistoryActor.deploy(injector, settings.orderFillSettings)
      )

      Routers.setRouters(
        OrderManagingActor.name,
        OrderManagingActor.deploy(injector, settings.orderManagerSettings)
      )

      Routers.setRouters(
        RingSubmitActor.name,
        RingSubmitActor.deploy(injector, settings.ringSubmitSettings)
      )

  }

}
