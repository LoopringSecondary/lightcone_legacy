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

trait ShardedByMarketId extends Sharded {
  val extractMarketId: PartialFunction[Any, String]

  private def hashed(msg: Any) = Math.abs(msg.hashCode % numOfShards)

  private def _extractEntityId(msg: Any): Option[(String, Any)] =
    (extractMarketId.lift)(msg).map { marketId ⇒ ("${name}_${hashed(msg)}_${marketId}", msg) }

  private def _extractShardId(msg: Any): Option[String] = Some("shard_" + hashed(msg))

  val extractEntityId: ShardRegion.ExtractEntityId = Function.unlift(_extractEntityId)
  val extractShardId: ShardRegion.ExtractShardId = Function.unlift(_extractShardId)

  def extractEntityName(actorName: String) = actorName.split("_").last
}

