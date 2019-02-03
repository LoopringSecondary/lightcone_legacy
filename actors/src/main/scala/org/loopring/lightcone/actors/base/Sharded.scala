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

import org.slf4s.Logging
import akka.actor._
import akka.cluster.sharding._
import akka.cluster.sharding.ShardRegion._
import com.typesafe.config.Config
import org.loopring.lightcone.proto.MarketPair
import org.loopring.lightcone.actors.data._

// Owner: Daniel

trait Sharded[T] extends Object with Logging {
  val name: String
  var numOfShards: Int = _

  val extractShardingObject: PartialFunction[Any, T]

  def getEntityId(obj: T): Long = Math.abs(obj.hashCode).toLong

  def messageExtractor = new HashCodeMessageExtractor(numOfShards) {
    override def entityId(msg: Any) =
      try {
        (extractShardingObject.lift)(msg)
          .map(getEntityId)
          .map(entityId => s"${name}_${entityId}")
          .getOrElse(null)
      } catch {
        case e: Throwable =>
          log.warn("unable to extract entityId", e)
          null
      }
  }

  def startSharding(
      entityProps: Props
    )(
      implicit
      system: ActorSystem,
      config: Config,
      deployActorsIgnoringRoles: Boolean
    ) = {
    val numOfShardsPath = s"${name}.num-of-shards"
    assert(
      config.hasPath(numOfShardsPath),
      s"no config for `${numOfShardsPath}`"
    )

    numOfShards = config.getInt(numOfShardsPath)
    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)

    ClusterSharding(system).start(
      typeName = name,
      entityProps = entityProps,
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }
}

trait ShardedEvenly extends Sharded[Any] {

  val extractShardingObject: PartialFunction[Any, Any] = {
    case msg: Any => msg
  }
}

trait ShardedByAddress extends Sharded[String]

// Owner: Daniel
trait ShardedByMarket extends Sharded[MarketPair] {
  override def getEntityId(marketPair: MarketPair): Long = marketPair.longId
}
