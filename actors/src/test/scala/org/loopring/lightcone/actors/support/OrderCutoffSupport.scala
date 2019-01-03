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

package org.loopring.lightcone.actors.support

import akka.actor.{PoisonPill, Props}
import akka.cluster.singleton.{
  ClusterSingletonManager,
  ClusterSingletonManagerSettings,
  ClusterSingletonProxy,
  ClusterSingletonProxySettings
}
import org.loopring.lightcone.actors.core._

trait OrderCutoffSupport extends DatabaseModuleSupport {
  my: CommonSpec =>

  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props(new OrderCutoffHandlerActor()),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ),
    OrderCutoffHandlerActor.name
  )
  actors.add(
    OrderCutoffHandlerActor.name,
    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${OrderCutoffHandlerActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${OrderCutoffHandlerActor.name}_proxy"
    )
  )
}
