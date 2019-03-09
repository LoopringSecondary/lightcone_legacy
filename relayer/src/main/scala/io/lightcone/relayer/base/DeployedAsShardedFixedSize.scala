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

import akka.actor._
import com.typesafe.config.Config
import io.lightcone.relayer.serializable._

// Owner: Daniel

trait DeployedAsShardedFixedSize[T] extends DeployedAsSharded[T] {
  var numOfEntities: Int = _

  // This method is used in tests to load configs.
  override def loadConfig()(implicit config: Config): Unit = {
    super.loadConfig()

    val numOfEntitiesPath = s"${name}.num-of-entities"
    if (config.hasPath(numOfEntitiesPath)) {
      numOfEntities = config.getInt(numOfEntitiesPath)
    } else {
      numOfEntities = numOfShards
    }
  }

  def broadcast[T <: scalapb.GeneratedMessage with scalapb.Message[T]](
      msg: T
    ) = {
    (0 until numOfEntities).foreach { entityId =>
      regionManager ! ShardingBroadcastEnvelope(
        longIdToStringName(entityId),
        msg
      )
    }
  }
}

trait DeployedAsShardedEvenly extends DeployedAsShardedFixedSize[Any] {

  val extractShardingObject: PartialFunction[Any, Any] = {
    case msg: Any => msg
  }

  def getEntityId(obj: Any) = (obj.hashCode % numOfEntities).abs
}

trait DeployedAsShardedWithMessageId extends DeployedAsShardedFixedSize[Long] {
  def getEntityId(id: Long) = id.abs
}

trait DeployedAsShardedByAddress extends DeployedAsShardedFixedSize[String] {

  def getEntityId(addr: String) = (addr.hashCode % numOfEntities).abs
}
