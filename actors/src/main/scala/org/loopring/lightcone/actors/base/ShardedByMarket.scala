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
import org.web3j.utils.Numeric
import akka.cluster.sharding.ShardRegion.HashCodeMessageExtractor

trait ShardedByMarket extends Sharded {
  val extractMarketId: PartialFunction[Any, XMarketId]

  def getEntityId(marketId: XMarketId): String = {
    val xorValue = Numeric.toBigInt(marketId.primary) xor
      Numeric.toBigInt(marketId.secondary)
    Math.abs(xorValue.hashCode % numOfShards).toString
  }

  def extractEntityId(actorName: String) = actorName.split("_").last

  val messageExtractor =
    new HashCodeMessageExtractor(numOfShards) {
      override def entityId(msg: Any) = {
        val entityIdOpt = (extractMarketId.lift)(msg).map(getEntityId)
        assert(entityIdOpt.isDefined, s"${msg} no entity id extracted")
        s"${name}_${entityIdOpt.get}"
      }
    }
}
