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

import akka.cluster.sharding._

trait Sharded {
  val numOfShards: Int
}

trait EventlySharded extends Sharded {
  val entitiesPerShard: Int

  private def getShardId(msg: Any) = Math.abs(msg.hashCode % numOfShards).toString

  private def getEntitityId(msg: Any) = {
    val entityId = Math.abs(msg.hashCode % entitiesPerShard)
    s"${getShardId(msg)}_${entityId}"
  }

  protected val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg ⇒ (getEntitityId(msg), msg)
  }

  protected val extractShardId: ShardRegion.ExtractShardId = {
    case msg ⇒ getShardId(msg)
  }
}

