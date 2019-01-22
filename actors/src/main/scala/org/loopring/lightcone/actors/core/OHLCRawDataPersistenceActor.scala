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

package org.loopring.lightcone.actors.core

import akka.actor._
import akka.cluster.sharding._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.lib.{ErrorException, TimeProvider}
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto.OHLCRawData
import org.loopring.lightcone.actors.base.safefuture._
import scala.concurrent.ExecutionContext

object OHLCRawDataPersistenceActor extends ShardedEvenly {
  val name = "ohlc_persistence"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new OHLCRawDataPersistenceActor()),
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }
}

class OHLCRawDataPersistenceActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(OHLCRawDataPersistenceActor.name) {

  def ready: Receive = {
    case data: OHLCRawData =>
      (for {
        saveRes <- dbModule.ohlcDataDal.saveData(data)
      } yield {
        saveRes.error match {
          case ERR_NONE =>
            saveRes.record
          case _ =>
            throw ErrorException(
              saveRes.error,
              s"failed to save ohlcRawData: $data"
            )
        }
      }) sendTo sender
  }
}
