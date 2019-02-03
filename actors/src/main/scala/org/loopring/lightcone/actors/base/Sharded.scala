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

package org.loopring.lightcone.actors.base

import akka.actor._
import akka.cluster.sharding._
import akka.cluster.sharding.ShardRegion._
import com.typesafe.config.Config

// Owner: Daniel

trait Sharded {
  val name: String
  val messageExtractor: MessageExtractor
  var numOfShards: Int = _

  def startSharding(
      entityProps: Props
    )(
      implicit
      system: ActorSystem,
      config: Config,
      deployActorsIgnoringRoles: Boolean
    ) = {
    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)

    ClusterSharding(system).start(
      typeName = name,
      entityProps = entityProps,
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }

}
