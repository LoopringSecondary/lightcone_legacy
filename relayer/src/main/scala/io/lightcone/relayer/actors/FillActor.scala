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

package io.lightcone.relayer.actors

import akka.actor.{Address => _, _}
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.ethereum.event.BlockEvent
import io.lightcone.ethereum.persistence.{Activity, Fill}
import io.lightcone.lib.TimeProvider
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import scala.concurrent._

// main owner: Yongfeng
object FillActor extends DeployedAsShardedByAddress {
  val name = "fill"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      databaseConfigManager: DatabaseConfigManager,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSharding(Props(new ActivityActor()))
  }

  // 如果message不包含一个有效的address，就不做处理，不要返回“默认值”
  val extractShardingObject: PartialFunction[Any, String] = {
    case req: Fill          => req.owner
    // TODO (yongfeng)：分片逻辑
    case req: GetFills.Req  => req.owner
  }
}

class FillActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule,
    val databaseConfigManager: DatabaseConfigManager)
    extends InitializationRetryActor
    with ShardingEntityAware {

  val selfConfig = config.getConfig(FillActor.name)
  val defaultItemsPerPage = selfConfig.getInt("default-items-per-page")
  val maxItemsPerPage = selfConfig.getInt("max-items-per-page")

  def ready: Receive = {

    case req: Fill =>
      dbModule.fillDal.saveFill(req)

    case req: GetFills.Req =>
      (for {
        fills <- dbModule.fillDal.getFills(req)
        count <- dbModule.fillDal.countFills(req)
        res = GetFills.Res(fills, count)
      } yield res).sendTo(sender)

    case req: BlockEvent =>
      (for{
        result <- dbModule.fillDal.clearForkedFills(req)
      } yield result).sendTo(sender)
  }

}
