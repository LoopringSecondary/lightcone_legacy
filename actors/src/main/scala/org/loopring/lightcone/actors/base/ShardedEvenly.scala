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
import akka.cluster.sharding.ShardRegion.HashCodeMessageExtractor

trait Sharded {
  val name: String
  protected var numOfShards: Int = 1
  protected def hashed(
      msg: Any,
      max: Int
    ): Int = Math.abs(msg.hashCode % max)
}

trait ShardedEvenly extends Sharded {
  protected var entitiesPerShard: Int = 1

  def getEntityId(message: Any) =
    Math.abs(message.hashCode % numOfShards * entitiesPerShard)

  protected val messageExtractor =
    new HashCodeMessageExtractor(numOfShards) {
      override def entityId(message: Any) = {
        val eid = getEntityId(message)
        s"${name}_${eid}"
      }
    }
}
