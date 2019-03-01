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
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.ethereum.event.{BlockEvent, BlockForkedEvent}
import io.lightcone.ethereum.persistence.Activity
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.persistence.dals._
import io.lightcone.relayer._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import org.web3j.utils.Numeric
import scala.concurrent._
import scala.util.{Failure, Success}

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
    case Notify(KeepAliveActor.NOTIFY_MSG, address) =>
      Numeric.toHexStringWithPrefix(BigInt(address).bigInteger)
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

  val mediator = DistributedPubSub(context.system).mediator

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

  override def initialize() = {
    val f = for {
      // TODO (yongfeng) 等亚东pub出分叉事件后sub BlockForkedEvent
      _ <- Future.successful()
    } yield {}

    f onComplete {
      case Success(_) =>
        becomeReady()
      case Failure(e) => throw e
    }
    f
  }

  def ready: Receive = {

    // TODO (yongfeng) 处理存储owner的activity（sequenceId需要保证唯一）
    // 新增字段 from, nonce, status，
    // 1 如果activity是pending，直接存
    // 2 如果activity在块中
    //    1. delete pending where owner, sequenceId (删除当前这一条的pending)
    //    3. insert blocked activity (打入块activity)
    case req: Activity => {
      if (req.block > 0) {
        val pendingSequenceId = 0L
        for {
          _ <- activityDal.deleteBySequenceId(req.owner, pendingSequenceId)
          _ <- activityDal.saveActivity(req)
        } yield {}
      } else {
        activityDal.saveActivity(req)
      }
    }

    // TODO (yongfeng) 订阅tx被打入块消息通知，更新当前分区fromAddress pending失效
    // update status = failed where from = ?, blockNum = 0, from nonce <= ? and txHash != ? (更新nonce小的pending为失败)
      // 或者先从pending activity里查询出需要更新失败的再更新
    case req: BlockEvent => Future.sequence(req.txs.map(t=> activityDal.updatePendingActivityFailed(t.from, t.nonce, t.txHash)))

    // TODO (yongfeng) 订阅分叉事件，更新当前分区所有影响块的activity为pending
    // 1. update activity set block = 0, status = PENDING where block >= ?
    // 2. 新链上块的activity由Activity事件重新处理
    // 3. fromAddress pending nonce过小的由BlockEvent事件处理
    case req: BlockForkedEvent =>
      activityDal.clearBlockDataSinceBlock(req.forkedBlockNum)

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
