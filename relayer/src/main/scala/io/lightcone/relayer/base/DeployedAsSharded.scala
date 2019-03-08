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
import io.lightcone.relayer.serializable._

// Owner: Daniel

trait DeployedAsSharded[T] extends Object with Logging {
  val name: String
  var numOfShards: Int = _

  protected var regionManager: ActorRef = _
  protected val extractShardingObject: PartialFunction[Any, T]

  def getEntityId(obj: T): Long

  def messageExtractor = new HashCodeMessageExtractor(numOfShards) {

    override def entityMessage(msg: Any): Any = msg match {
      case ShardingBroadcastEnvelope(entityId, enclosed) => enclosed
      case _                                             => msg
    }

    def entityId(msg: Any): String = msg match {
      case ShardingBroadcastEnvelope(entityId, _) => entityId

      case _ =>
        try {
          (extractShardingObject.lift)(msg)
            .map(getEntityId)
            .map(longIdToStringName)
            .getOrElse(null)
        } catch {
          case e: Throwable =>
            log.warn("unable to extract entityId", e)
            null
        }
    }
  }

  // This method is used in tests to load configs.
  def loadConfig()(implicit config: Config): Unit = {
    val numOfShardsPath = s"${name}.num-of-shards"
    assert(
      config.hasPath(numOfShardsPath),
      s"no config for `${numOfShardsPath}`"
    )
    numOfShards = config.getInt(numOfShardsPath)
    assert(numOfShards > 0, "num of shards should be greater than 0")
  }

  @inline protected def longIdToStringName(id: Long) = s"${name}_${id}"

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

    regionManager = ClusterSharding(system).start(
      typeName = name,
      entityProps = entityProps,
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
    regionManager
  }
}

trait DeployedAsShardedByMarket extends DeployedAsSharded[MarketPair] {
  def getEntityId(marketPair: MarketPair) = marketPair.longId
}
