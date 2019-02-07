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

package io.lightcone.relayer.base

import akka.actor._
import akka.cluster.singleton._
import com.typesafe.config.Config

// Owner: Daniel

trait DeployedAsSingleton {
  val name: String

  def startSingleton(
      singletonProps: Props
    )(
      implicit
      system: ActorSystem,
      deployActorsIgnoringRoles: Boolean
    ) = {

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = singletonProps,
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${name}_proxy"
    )
  }
}
