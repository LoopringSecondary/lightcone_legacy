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
import akka.cluster.sharding.ShardRegion.HashCodeMessageExtractor
import com.typesafe.config.Config

// Owner: Daniel
trait ShardedByAddress extends Sharded {
  val extractAddress: PartialFunction[Any, String]

  def getEntityId(
      address: String,
      numOfShards: Int
    ): String =
    Math.abs(address.hashCode % numOfShards).toString

  def getEntityId(address: String): String =
    getEntityId(address, numOfShards)

  def messageExtractor =
    new HashCodeMessageExtractor(numOfShards) {
      override def entityId(msg: Any) = {
        val entityIdOpt = (extractAddress.lift)(msg).map(getEntityId)
        assert(entityIdOpt.isDefined, s"${msg} no entity id extracted")
        s"${name}_${entityIdOpt.get}"
      }
    }
}
