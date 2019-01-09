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
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto.{TransactionRecord, _}
import scala.concurrent._

// main owner: 杜永丰
object EthereumEventAccessActor extends ShardedByAddress {
  val name = "ethereum_event_access"

  def startShardRegion(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new EthereumEventAccessActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      messageExtractor = messageExtractor
    )
  }

  // 如果message不包含一个有效的address，就不做处理，不要返回“默认值”
  val extractAddress: PartialFunction[Any, String] = {
    case req: TransferEvent =>
      req.owner
    case req: CutoffEvent =>
      req.owner
    case req: OrdersCancelledEvent =>
      req.owner
    case req: OrderFilledEvent =>
      req.owner
    case req: GetTransactions.Req =>
      req.owner
  }
}

class EthereumEventAccessActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(EthereumEventAccessActor.name) {

  def receive: Receive = {
    // ETH & ERC20
    case req: TransferEvent =>
      val header = req.header.get
      val recordType =
        if (req.token.nonEmpty) TransactionRecord.RecordType.ERC20_TRANSFER
        else TransactionRecord.RecordType.TRANSFER
      val record = TransactionRecord(
        header = req.header,
        owner = req.owner,
        recordType = recordType,
        createdAt = timeProvider.getTimeMillis(),
        eventData = Some(
          TransactionRecord
            .EventData(TransactionRecord.EventData.Event.Transfer(req))
        ),
        shardEntity = EthereumEventAccessActor.getEntityId(req.owner),
        sequenceId = EventAccessProvider.generateSequenceId(
          header.blockNumber,
          header.txIndex,
          header.logIndex
        )
      )
      dbModule.transactionRecordService.saveRecord(record)

    case req: OrdersCancelledEvent =>
      val header = req.header.get
      val record = TransactionRecord(
        header = req.header,
        owner = req.owner,
        recordType = TransactionRecord.RecordType.ORDER_CANCELLED,
        createdAt = timeProvider.getTimeMillis(),
        eventData = Some(
          TransactionRecord
            .EventData(TransactionRecord.EventData.Event.OrderCancelled(req))
        ),
        shardEntity = EthereumEventAccessActor.getEntityId(req.owner),
        sequenceId = EventAccessProvider.generateSequenceId(
          header.blockNumber,
          header.txIndex,
          header.logIndex
        )
      )
      dbModule.transactionRecordService.saveRecord(record)

    case req: CutoffEvent =>
      val header = req.header.get
      val record = TransactionRecord(
        header = req.header,
        owner = req.owner,
        recordType = TransactionRecord.RecordType.ORDER_CANCELLED,
        tradingPair = req.tradingPair,
        createdAt = timeProvider.getTimeMillis(),
        eventData = Some(
          TransactionRecord
            .EventData(TransactionRecord.EventData.Event.Cutoff(req))
        ),
        shardEntity = EthereumEventAccessActor.getEntityId(req.owner),
        sequenceId = EventAccessProvider.generateSequenceId(
          header.blockNumber,
          header.txIndex,
          header.logIndex
        )
      )
      dbModule.transactionRecordService.saveRecord(record)

    case req: OrderFilledEvent =>
      //TODO du：是否需要查询并验证订单存在
      for {
        order <- dbModule.orderService.getOrder(req.orderHash)
        saved = if (order.isEmpty) {
          Future.successful(
            PersistTransactionRecord
              .Res(error = ErrorCode.ERR_ORDER_VALIDATION_NOT_PERSISTED)
          )
        } else {
          val header = req.header.get
          val marketHash =
            MarketHashProvider.convert2Hex(order.get.tokenS, order.get.tokenB)
          val record = TransactionRecord(
            header = req.header,
            owner = req.owner,
            recordType = TransactionRecord.RecordType.ORDER_FILLED,
            tradingPair = marketHash,
            createdAt = timeProvider.getTimeMillis(),
            eventData = Some(
              TransactionRecord
                .EventData(TransactionRecord.EventData.Event.Filled(req))
            ),
            shardEntity = EthereumEventAccessActor.getEntityId(req.owner),
            sequenceId = EventAccessProvider.generateSequenceId(
              header.blockNumber,
              header.txIndex,
              header.logIndex
            )
          )
          dbModule.transactionRecordService.saveRecord(record)
        }
      } yield saved

    case req: GetTransactions.Req =>
      val paging = req.paging.getOrElse(CursorPaging(0, 50))
      dbModule.transactionRecordService
        .getRecordsByOwner(req.owner, req.queryType, req.sort, paging)
        .map(GetTransactions.Res(_))
        .sendTo(sender)

  }

}
