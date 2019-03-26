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

package io.lightcone.relayer.integration.recovery

import io.lightcone.core.MarketPair
import io.lightcone.relayer.actors.{
  MarketManagerActor,
  MultiAccountManagerActor,
  OrderbookManagerActor
}
import io.lightcone.relayer.integration._
import io.lightcone.relayer.data._

trait RecoveryHelper {

  private def getEntityNums(name: String) =
    config.getInt(s"$name.num-of-entities")
  private def getShardNums(name: String) =
    config.getInt(s"${name}.num-of-shards")

  private def getShardId(
      entityId: Long,
      name: String
    ) = {
    val shardStr = s"${name}_$entityId"
    (shardStr.hashCode % getShardNums(name)).abs
  }

  private def getAccountManagerEntityId(addr: String) =
    (addr.hashCode % getEntityNums(MultiAccountManagerActor.name)).abs

  private def getMarketManagerEntityId(marketPair: MarketPair) =
    marketPair.longId

  private def getOrderBookEntityId(marketPair: MarketPair) = marketPair.longId

  private def getShardActorPath(
      name: String,
      entityId: Long,
      shardId: Long
    ) = s"/system/sharding/$name/$shardId/${name}_$entityId"

  def getAccountManagerShardActor(addr: String) = {
    val entityId = getAccountManagerEntityId(addr)
    val shardId = getShardId(entityId, MultiAccountManagerActor.name)
    val path =
      getShardActorPath(MultiAccountManagerActor.name, entityId, shardId)
    system.actorSelection(path)
  }

  def getMarketManagerShardActor(marketPair: MarketPair) = {
    val entityId = getMarketManagerEntityId(marketPair)
    val shardId = getShardId(entityId, MarketManagerActor.name)
    val path = getShardActorPath(MarketManagerActor.name, entityId, shardId)
    system.actorSelection(path)
  }

  def getOrderBookShardActor(marketPair: MarketPair) = {
    val entityId = getOrderBookEntityId(marketPair)
    val shardId = getShardId(entityId, OrderbookManagerActor.name)
    val path = getShardActorPath(OrderbookManagerActor.name, entityId, shardId)
    system.actorSelection(path)
  }

}
