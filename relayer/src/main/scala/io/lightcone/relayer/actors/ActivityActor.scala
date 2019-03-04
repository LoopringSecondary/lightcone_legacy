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
import io.lightcone.ethereum.persistence.Activity
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
    case req: Activity          => req.owner
    case req: GetActivities.Req => req.owner
    // TODO (yongfeng)：分片逻辑待完善
    case req: BlockEvent => req.shardKey
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

    case req: Activity =>
      activityDal.saveActivity(req)

    case req: BlockEvent =>
      (for {
        _ <- activityDal.clearBlockActivities(req)
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

//  private def clearPendingWithBlock(req: BlockEvent) =
//    for {
//      pendingActivities <- activityDal.getPendingActivities(
//        req.txs.map(_.from).toSet
//      )
//
//      successTxHashes = req.txs.map(_.txHash)
//
//      inLowerNonce = req.txs.map { t =>
//        pendingActivities.find(
//          p =>
//            (p.from == t.from && p.nonce == t.nonce && p.txHash != t.txHash) || (p.from == t.from && p.nonce < t.nonce && !successTxHashes
//              .contains(p.txHash))
//        )
//      }.filter(_.isDefined)
//
//      _ <- Future.sequence(inLowerNonce.map { t =>
//        val activity = t.get
//        activityDal.deleteBySequenceId(activity.sequenceId)
//      })
//    } yield {}

}
