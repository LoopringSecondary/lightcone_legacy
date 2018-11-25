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
import akka.cluster.Cluster
import com.google.inject.{ AbstractModule, PrivateModule, Singleton }
import net.codingwell.scalaguice.{ ScalaModule, ScalaPrivateModule }

class BaseDeploymentModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    super.configure()

    val system = ActorSystem()
    bind[ActorSystem].toInstance(system)
    bind[Cluster].toInstance(Cluster(system))

    bind[Lookup[ActorRef]].toInstance(new MapBasedLookup[ActorRef]())
    bind[Lookup[Props]].toInstance(new MapBasedLookup[Props]())
    bind[ActorDeployer].to[ActorDeployerImpl].in[Singleton]
  }
}
