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
import org.loopring.lightcone.proto.XMarketId

trait ShardedByMarket extends Sharded {
  val extractMarketName: PartialFunction[Any, BigInt]

  def hashed(msg: Option[BigInt]) = {
    msg.get.bigInteger.mod(BigInt(numOfShards)).abs.toInt
  }

  private def _extractEntityId(msg: Any): Option[(String, Any)] = {
    val entity = hashed((extractMarketName.lift)(msg))
    Some((s"${name}_${entity}", msg))
  }

  private def _extractShardId(msg: Any): Option[String] = {
    val entity = hashed((extractMarketName.lift)(msg))
    Some(s"shard_${entity}")
  }

  val extractEntityId: ShardRegion.ExtractEntityId =
    Function.unlift(_extractEntityId)

  val extractShardId: ShardRegion.ExtractShardId =
    Function.unlift(_extractShardId)

  def extractEntityName(actorName: String) = actorName.split("_").last
}
