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
import io.lightcone.ethereum.persistence._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.persistence.dals._
import io.lightcone.relayer._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import scala.concurrent._

// main owner: 杜永丰
object ActivityActor extends DeployedAsShardedByAddress {
  val name = "activity"

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
    case req: GetActivities.Req => req.owner
    // TODO (yongfeng)：分片逻辑待完善
    case req: TxEvents   => "0x0"
    case req: BlockEvent => "0x0"
  }

}

class ActivityActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule,
    val databaseConfigManager: DatabaseConfigManager)
    extends InitializationRetryActor
    with ShardingEntityAware {

  val selfConfig = config.getConfig(ActivityActor.name)
  val defaultItemsPerPage = selfConfig.getInt("default-items-per-page")
  val maxItemsPerPage = selfConfig.getInt("max-items-per-page")

  val dbConfigKey = s"db.activity.entity_${entityId}"
  log.info(
    s"ActivityActor with db configuration: $dbConfigKey ",
    s"- ${config.getConfig(dbConfigKey)}"
  )

  val activityDal: ActivityDal =
    new ActivityDalImpl(
      shardId = entityId.toString,
      databaseConfigManager.getDatabaseConfig(dbConfigKey)
    )

  activityDal.createTable()

  def ready: Receive = {

    // TODO yongfeng: 这个事件可以发给所有分片，每个分片过滤自己需要存储的activity ，一组activities txHash应一致
    //  分片广播逻辑确认后可能会修改这里
    case req: TxEvents => {
      // filter activities which current shard care
      val activities = req.getActivities.events
        .filter(a => ActivityActor.getEntityId(a.owner) == entityId)
      if (activities.nonEmpty) activityDal.saveActivities(activities)
      else Future.successful(Unit)
    }

    case req: BlockEvent =>
      (for {
        _ <- activityDal.cleanUpForBlockReorganization(req)
      } yield {}).sendTo(sender)

    case req: GetActivities.Req =>
      (for {
        activities <- activityDal.getActivities(
          req.owner,
          req.token,
          req.paging.get
        )
        res = GetActivities.Res(activities)
      } yield res).sendTo(sender)

  }

}
