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

package io.lightcone.relayer.base

import org.slf4s.Logging
import akka.actor._
import akka.cluster.sharding._
import akka.cluster.sharding.ShardRegion._
import com.typesafe.config.Config
import io.lightcone.core._
import io.lightcone.relayer.data._

// Owner: Daniel

trait DeployedAsSharded[T] extends Object with Logging {
  val name: String
  var numOfShards: Int = _
  var numOfEntities: Int = _

  protected val extractShardingObject: PartialFunction[Any, T]

  def getEntityId(obj: T): Long

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

  // This method is used in tests to load configs.
  def loadConfig()(implicit config: Config) {
    val numOfShardsPath = s"${name}.num-of-shards"
    assert(
      config.hasPath(numOfShardsPath),
      s"no config for `${numOfShardsPath}`"
    )
    numOfShards = config.getInt(numOfShardsPath)
    assert(numOfShards > 0, "num of shards should be greater than 0")

    val numOfEntitiesPath = s"${name}.num-of-entities"
    if (config.hasPath(numOfEntitiesPath)) {
      numOfEntities = config.getInt(numOfEntitiesPath)
    } else {
      numOfEntities = numOfShards
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
    loadConfig()
    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)

    ClusterSharding(system).start(
      typeName = name,
      entityProps = entityProps,
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }
}

trait DeployedAsShardedEvenly extends DeployedAsSharded[Any] {

  val extractShardingObject: PartialFunction[Any, Any] = {
    case msg: Any => msg
  }

  def getEntityId(obj: Any) = (obj.hashCode % numOfEntities).abs
}

trait DeployedAsShardedWithMessageId extends DeployedAsSharded[Long] {
  def getEntityId(id: Long) = id.abs
}

trait DeployedAsShardedByAddress extends DeployedAsSharded[String] {

  def getEntityId(addr: String) = (addr.hashCode % numOfEntities).abs
}

// Owner: Daniel
trait DeployedAsShardedByMarket extends DeployedAsSharded[MarketPair] {
  def getEntityId(marketPair: MarketPair) = marketPair.longId
}
