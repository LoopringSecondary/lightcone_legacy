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

package org.loopring.lightcone.actors

import com.google.inject.Guice
import com.typesafe.config.ConfigFactory
import org.slf4s.Logging
import net.codingwell.scalaguice.InjectorExtensions._
import akka.actor.ActorRef
import com.google.inject.Inject
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.core._
import akka.cluster._
import akka.actor._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import scala.concurrent._

class ClusterDeployer @Inject() (
  implicit system: ActorSystem,
  config: Config,
  ec: ExecutionContext,
  timeProvider: TimeProvider,
  timeout: Timeout,
  actors: Lookup[ActorRef])
  extends Object
  with Logging {

  def deploy() {
    // bind[DatabaseModule].in[Singleton]
    // dbModule.createTables()

    Cluster(system).registerOnMemberUp {

      actors.add(
        EthereumEventExtractorActor.name,
        EthereumEventExtractorActor.startShardRegion())
    }
  }
}
