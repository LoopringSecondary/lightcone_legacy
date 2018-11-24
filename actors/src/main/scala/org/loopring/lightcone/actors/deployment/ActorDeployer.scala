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

case class NodeDeploymentSettings(
    settingsMap: Map[String, ActorDeploymentSettings] = Map.empty
)

case class ActorDeploymentSettings(
    isSingleton: Boolean,
    props: Props,
    roles: Set[String],
    numOfInstances: Int
)

class ActorDeployer(actorLookup: ActorLookup)(
    implicit
    cluster: Cluster
) {

  private var lastInstanceId: Int = 0
  private def nextInstanceId = {
    lastInstanceId += 1
    lastInstanceId
  }

  private var nodeDeploymentSettings = NodeDeploymentSettings()

  def deploy(newNodeDeploymentSettings: NodeDeploymentSettings) = {

    val names = nodeDeploymentSettings.settingsMap.keys ++
      newNodeDeploymentSettings.settingsMap.keys

    names.foreach { name ⇒
      val oldSettings = nodeDeploymentSettings.settingsMap.get(name)
      val newSettings = newNodeDeploymentSettings.settingsMap.get(name)

      deployActors(name, oldSettings, newSettings).foreach {
        router ⇒ actorLookup.addActor(name, router)
      }

    }

    nodeDeploymentSettings = newNodeDeploymentSettings
  }

  // Return an optional router to the new actors
  def deployActors(
    name: String,
    oldSettings: Option[ActorDeploymentSettings],
    newSettings: Option[ActorDeploymentSettings]
  ): Option[ActorRef] = this.synchronized {
    null
  }

  // def deploy(settings: ActorDeploymentSettings) = this.synchronized {
  //   val name = settings.name.toLowerCase()
  //   val previousSettingsOpt = settingsMap.get(name)
  //   settingsMap += name -> settings

  //   // Step 1: (re)deploy routers
  //   if (Option(settings) != previousSettingsOpt) {
  //     val selectionPattern = s"/user/r_${name}_*"
  //     println(s"--------> killing router: ${selectionPattern}")
  //     cluster.system.actorSelection(selectionPattern) ! PoisonPill
  //   }

  //   val routerRef = if (settings.isSingleton) {
  //     cluster.system.actorOf(
  //       ClusterSingletonProxy.props(
  //         singletonManagerPath = s"/user/${name}_0",
  //         settings = ClusterSingletonProxySettings(cluster.system)
  //       ),
  //       name = s"r_${name}"
  //     )
  //   } else {
  //     cluster.system.actorOf(
  //       ClusterRouterGroup(
  //         RoundRobinGroup(Nil),
  //         ClusterRouterGroupSettings(
  //           totalInstances = Int.MaxValue,
  //           routeesPaths = List(s"/user/${name}_*"),
  //           allowLocalRoutees = true
  //         )
  //       ).props,
  //       name = s"r_${name}_${nextInstanceId}"
  //     )
  //   }

  //   println("--------> deployed router: " + routerRef.path)
  //   actorLookup.addActor(settings.name, routerRef)
  // }
}

