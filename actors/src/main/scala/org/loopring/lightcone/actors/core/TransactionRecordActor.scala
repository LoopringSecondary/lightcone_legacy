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
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.DatabaseConfigManager
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._
import scala.concurrent._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic.DatabaseConfig
import TransactionRecord.RecordType._
import TransactionRecord.EventData.Event

// main owner: 杜永丰
object TransactionRecordActor extends ShardedByAddress {
  val name = "transaction-record"

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

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new TransactionRecordActor()),
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }

  // 如果message不包含一个有效的address，就不做处理，不要返回“默认值”
  val extractAddress: PartialFunction[Any, String] = {
    case req: TransferEvent                 => req.owner
    case req: CutoffEvent                   => req.owner
    case req: OrdersCancelledEvent          => req.owner
    case req: OrderFilledEvent              => req.owner
    case req: GetTransactionRecords.Req     => req.owner
    case req: GetTransactionRecordCount.Req => req.owner
  }
}

class TransactionRecordActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule,
    val databaseConfigManager: DatabaseConfigManager)
    extends ActorWithPathBasedConfig(
      TransactionRecordActor.name,
      TransactionRecordActor.extractEntityId
    ) {
  val defaultItemsPerPage = selfConfig.getInt("default-items-per-page")
  val maxItemsPerPage = selfConfig.getInt("max-items-per-page")

  val dbConfigKey = s"db.transaction-record.shard_${entityId}"
  log.info(
    s"TransactionRecordActor with db configuration ($dbConfigKey): ",
    config.getConfig(dbConfigKey)
  )

  val txRecordDal: TransactionRecordDal =
    new TransactionRecordDalImpl(
      shardId = entityId,
      databaseConfigManager.getDatabaseConfig(dbConfigKey)
    )

  txRecordDal.createTable()

  def ready: Receive = {
    // ETH & ERC20
    case req: TransferEvent =>
      val header = req.header.get
      val recordType =
        if (req.token.nonEmpty) ERC20_TRANSFER
        else TRANSFER
      val record = TransactionRecord(
        header = req.header,
        owner = req.owner,
        recordType = recordType,
        eventData = Some(
          TransactionRecord
            .EventData(Event.Transfer(req))
        ),
        sequenceId = header.sequenceId
      )
      txRecordDal.saveRecord(record)

    case req: OrdersCancelledEvent =>
      val header = req.header.get
      val record = TransactionRecord(
        header = req.header,
        owner = req.owner,
        recordType = ORDER_CANCELLED,
        eventData = Some(
          TransactionRecord
            .EventData(Event.OrderCancelled(req))
        ),
        sequenceId = header.sequenceId
      )
      txRecordDal.saveRecord(record)

    case req: CutoffEvent =>
      val header = req.header.get
      val record = TransactionRecord(
        header = req.header,
        owner = req.owner,
        recordType = ORDER_CANCELLED,
        marketKey = req.marketKey,
        eventData = Some(
          TransactionRecord
            .EventData(Event.Cutoff(req))
        ),
        sequenceId = header.sequenceId
      )
      txRecordDal.saveRecord(record)

    case req: OrderFilledEvent =>
      for {
        order <- dbModule.orderService.getOrder(req.orderHash)
        header = req.header.get
        marketHash = if (order.isEmpty) ""
        else MarketHashProvider.convert2Hex(order.get.tokenS, order.get.tokenB)
        record = TransactionRecord(
          header = req.header,
          owner = req.owner,
          recordType = ORDER_FILLED,
          marketKey = marketHash,
          eventData = Some(
            TransactionRecord
              .EventData(Event.Filled(req))
          ),
          sequenceId = header.sequenceId
        )
        saved <- txRecordDal.saveRecord(record)
      } yield saved

    case req: GetTransactionRecords.Req =>
      txRecordDal
        .getRecordsByOwner(req.owner, req.queryType, req.sort, req.paging.get)
        .map(GetTransactionRecords.Res(_))
        .sendTo(sender)

    case req: GetTransactionRecordCount.Req =>
      txRecordDal
        .getRecordsCountByOwner(req.owner, req.queryType)
        .map(GetTransactionRecordCount.Res(_))
        .sendTo(sender)
  }

}
