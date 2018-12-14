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

trait ShardedByAddress extends Sharded {
  val extractAddress: PartialFunction[Any, String]

  private def hashed(msg: Any): Option[Int] =
    (extractAddress.lift)(msg).map { address =>
      Math.abs(address.hashCode % numOfShards)
    }

  private def _extractEntityId(msg: Any): Option[(String, Any)] =
    hashed(msg).map { shard =>
      ("${name}_${shard}_${marketId}", msg)
    }

  private def _extractShardId(msg: Any): Option[String] =
    hashed(msg).map("shard_" + _)

  val extractEntityId: ShardRegion.ExtractEntityId =
    Function.unlift(_extractEntityId)

  val extractShardId: ShardRegion.ExtractShardId =
    Function.unlift(_extractShardId)

  def extractEntityName(actorName: String) = actorName.split("_").last
}
