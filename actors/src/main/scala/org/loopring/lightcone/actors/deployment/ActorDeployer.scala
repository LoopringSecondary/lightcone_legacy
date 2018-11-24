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

package org.loopring.lightcone.actors.deployment

import akka.actor._
import akka.cluster._
import akka.routing._
import akka.cluster.singleton._
import akka.cluster.routing._
import org.slf4s.Logging

case class NodeDeploymentSettings(
    settingsMap: Map[String, ActorDeploymentSettings] = Map.empty
)

case class ActorDeploymentSettings(
    isSingleton: Boolean,
    props: Props,
    roles: Set[String],
    numOfInstances: Int
)

class ActorDeployer(
    actorLookup: ActorLookup,
    propsLookup: PropsLookup
)(
    implicit
    cluster: Cluster
) extends Object with Logging {

  private var lastInstanceId: Int = 0
  private def nextInstanceId = {
    lastInstanceId += 1
    lastInstanceId
  }

  private val system = cluster.system
  private var nodeDeploymentSettings = NodeDeploymentSettings()

  def deploy(newNodeDeploymentSettings: NodeDeploymentSettings) = {

    val names = nodeDeploymentSettings.settingsMap.keys ++
      newNodeDeploymentSettings.settingsMap.keys

    names.foreach { name ⇒
      val oldSettings = nodeDeploymentSettings.settingsMap.get(name)
      val newSettings = newNodeDeploymentSettings.settingsMap.get(name)

      deployActors(name, oldSettings, newSettings)
    }

    nodeDeploymentSettings = newNodeDeploymentSettings
  }

  // Return an optional router to the new actors
  def deployActors(
    name: String,
    oldSettings: Option[ActorDeploymentSettings],
    newSettings: Option[ActorDeploymentSettings]
  ) = this.synchronized {

    // Step 1: (re)deploy routers
    if (newSettings.isEmpty) {
      destroyActorRouter(name)
    }

  }

  private def deployActorRouter(name: String) = {
    val router = system.actorOf(
      ClusterRouterGroup(
        RoundRobinGroup(Nil),
        ClusterRouterGroupSettings(
          totalInstances = Int.MaxValue,
          routeesPaths = List(s"/user/${name}_*"),
          allowLocalRoutees = true
        )
      ).props,
      name = s"r_${name}"
    )
    actorLookup.addActor(name, router)
    log.info(s"--------> deployed router for singleton: ${router.path}")
  }

  private def deploySingletonActorRouter(name: String) = {
    val router = system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${name}_0",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"r_${name}"
    )
    actorLookup.addActor(name, router)
    log.info(s"--------> deployed router: ${router.path}")
  }

  private def destroyActorRouter(name: String) = {
    val selectionPattern = s"/user/r_${name}"
    log.info(s"--------> killing router: ${selectionPattern}")
    system.actorSelection(selectionPattern) ! PoisonPill
    actorLookup.removeActor(name)
  }

  private def deployActor(name: String) = {
    val actor = cluster.system.actorOf(
      propsLookup.getProps(name),
      name = s"${name}_${nextInstanceId}"
    )
    log.info(s"--------> deployed actor: ${actor.path}")
  }

  private def deploySingletonActor(name: String) = {
    val actor = cluster.system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = propsLookup.getProps(name),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
      ),
      name = s"${name}_0"
    )
    log.info(s"--------> deployed singleton actor: ${actor.path}")
  }

  private def destroyActor(name: String) = {
    val selectionPattern = s"/user/${name}_*"
    log.info(s"--------> killing actor: ${selectionPattern}")
    system.actorSelection(selectionPattern) ! PoisonPill
  }
}

