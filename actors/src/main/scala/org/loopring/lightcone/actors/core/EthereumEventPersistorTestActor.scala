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

package org.loopring.lightcone.actors.core

import akka.actor._
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto.ErrorCode.ERR_UNEXPECTED_ACTOR_MSG
import org.loopring.lightcone.proto._

import scala.concurrent._

object EthereumEventPersistorTestActor extends ShardedByAddress {
  val name = "ethereum_event_persister_test"

  def startShardRegion(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef]
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new EthereumEventPersistorActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      messageExtractor = messageExtractor
    )
  }

  val extractAddress: PartialFunction[Any, String] = {
    case req: SubmitOrder.Req =>
      req.rawOrder
        .map(_.owner)
        .getOrElse {
          throw ErrorException(
            ERR_UNEXPECTED_ACTOR_MSG,
            "SubmitOrder.Req.rawOrder must be nonEmpty."
          )
        }
  }
}

class EthereumEventPersistorTestActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends ActorWithPathBasedConfig(EthereumEventPersistorTestActor.name) {

  val txPersistorActors = new MapBasedLookup[ActorRef]()

  def receive: Receive = {
    case _ =>
  }

}
