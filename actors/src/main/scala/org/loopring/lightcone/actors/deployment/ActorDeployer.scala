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
    actorLookup: Lookup[ActorRef],
    propsLookup: Lookup[Props]
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

  def deploy(
    newNodeDeploymentSettings: NodeDeploymentSettings,
    actorConfigMap: Map[String, AnyRef]
  ) = {
    val names = nodeDeploymentSettings.settingsMap.keys ++
      newNodeDeploymentSettings.settingsMap.keys

    names.foreach { name ⇒
      val oldSettings = nodeDeploymentSettings.settingsMap.get(name)
      val newSettings = newNodeDeploymentSettings.settingsMap.get(name)
      val config = actorConfigMap.get(name)
      deployActors(name, oldSettings, newSettings, config)
    }

    nodeDeploymentSettings = newNodeDeploymentSettings
  }

  // Return an optional router to the new actors
  def deployActors(
    name: String,
    oldSettings: Option[ActorDeploymentSettings],
    newSettings: Option[ActorDeploymentSettings],
    configOpt: Option[AnyRef]
  ) = this.synchronized {

    // deploy actor routers
    if (newSettings.isEmpty) {
      destroyActorRouter(name)
    } else {
      if (newSettings.get.isSingleton) deploySingletonActorRouter(name)
      else deployActorRouter(name)
    }

    // deploy actors
    val oldNumOfInstances = oldSettings.map(getNumInstance).getOrElse(0)
    val newNumOfInstances = newSettings.map(getNumInstance).getOrElse(0)

    val extraNumOfInstance =
      if (oldNumOfInstances > newNumOfInstances) {
        destroyActor(name)
        newNumOfInstances
      } else {
        newNumOfInstances - oldNumOfInstances
      }

    (0 to extraNumOfInstance) foreach { _ ⇒
      if (newSettings.get.isSingleton) deploySingletonActor(name)
      else deployActor(name)
    }

    reconfigActor(name, configOpt)
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
    actorLookup.add(name, router)
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
    actorLookup.add(name, router)
    log.info(s"--------> deployed router: ${router.path}")
  }

  private def destroyActorRouter(name: String) = {
    val selectionPattern = s"/user/r_${name}"
    log.info(s"--------> killing router: ${selectionPattern}")
    system.actorSelection(selectionPattern) ! PoisonPill
    actorLookup.del(name)
  }

  private def deployActor(name: String) = {
    val actor = cluster.system.actorOf(
      propsLookup.get(name),
      name = s"${name}_${nextInstanceId}"
    )
    log.info(s"--------> deployed actor: ${actor.path}")
  }

  private def deploySingletonActor(name: String) = {
    val actor = cluster.system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = propsLookup.get(name),
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

  private def reconfigActor(name: String, configOpt: Option[AnyRef]) = {
    configOpt.foreach { config ⇒
      val selectionPattern = s"/user/${name}_*"
      log.info(s"--------> reconfig actor: ${selectionPattern}")
      system.actorSelection(selectionPattern) ! config
    }
  }

  private def getNumInstance(settings: ActorDeploymentSettings) = {
    if (cluster.selfRoles.intersect(settings.roles).isEmpty) 0
    else if (settings.isSingleton) 1
    else settings.numOfInstances
  }
}

