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
import io.lightcone.core._
import io.lightcone.ethereum.event._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.persistence.dals._
import io.lightcone.relayer._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.persistence.Activity

import scala.concurrent._

class ActivityActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule,
    val tokenValueEvaluator: TokenValueEvaluator,
    val databaseConfigManager: DatabaseConfigManager)
    extends InitializationRetryActor
    with ShardingEntityAware {

  import Activity.ActivityType._

  val selfConfig = config.getConfig(TransactionRecordActor.name)
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
    // ETH & ERC20
    case evt: TransferEvent =>
      val header = evt.header.get
      val token = Address(evt.token)
      val (activityType, detail) =
        if (token.isZero) {
          val actType =
            if (evt.from == evt.owner) ETHER_TRANSFER_OUT
            else ETHER_TRANSFER_IN
          (
            actType,
            Activity.Detail.EtherTransfer(
              Activity.EtherTransfer(evt.owner, evt.amount)
            )
          )
        } else {
          val actType =
            if (evt.from == evt.owner) TOKEN_TRANSFER_OUT
            else TOKEN_TRANSFER_IN
          (
            actType,
            Activity.Detail.TokenTransfer(
              Activity.TokenTransfer(evt.owner, evt.token, evt.amount)
            )
          )
        }
      val activity = Activity(
        owner = evt.owner,
        token = evt.token,
        activityType = activityType,
        txHash = evt.getHeader.txHash,
        timestamp = evt.getHeader.getBlockHeader.timestamp,
        fiatValue = tokenValueEvaluator.getValue(token.toString(), evt.amount),
        sequenceId = evt.getHeader.sequenceId(),
        detail = detail
      )
      activityDal.saveActivity(activity)

    case req: GetAccountActivities.Req =>
      activityDal
        .getActivities(req.owner, req.token, req.paging.get)
        .map(GetAccountActivities.Res(_))
        .sendTo(sender)

    //TODO:是否应该出现在Activity中
    case evt: OrdersCancelledOnChainEvent =>
      val header = evt.header.get
      val detail = Activity.Detail.OrderCancellation(
        Activity.OrderCancellation(
          orderId = evt.orderHashes,
          broker = evt.broker //TODO:marketpair等需要补全
        )
      )

      val activity = Activity(
        owner = evt.owner,
        activityType = ORDER_CANCEL,
        txHash = evt.getHeader.txHash,
        timestamp = evt.getHeader.getBlockHeader.timestamp,
        sequenceId = evt.getHeader.sequenceId(),
        detail = detail
      )

      activityDal.saveActivity(activity)

//    case req: CutoffEvent =>
//      val header = req.header.get
//      val record = TransactionRecord(
//        header = req.header,
//        owner = req.owner,
//        recordType = ORDER_CANCELLED,
//        marketId = MarketHash.hashToId(req.marketHash),
//        eventData = Some(
//          TransactionRecord
//            .EventData(Event.Cutoff(req))
//        ),
//        sequenceId = header.sequenceId
//      )
//      activityDal.saveRecord(record)
//
//    case req: OrderFilledEvent =>
//      for {
//        order <- dbModule.orderService.getOrder(req.orderHash)
//        header = req.header.get
//        marketHash = if (order.isEmpty) ""
//        else MarketHash(MarketPair(order.get.tokenS, order.get.tokenB)).toString
//        record = TransactionRecord(
//          header = req.header,
//          owner = req.owner,
//          recordType = ORDER_FILLED,
//          marketId = MarketHash.hashToId(marketHash),
//          eventData = Some(
//            TransactionRecord
//              .EventData(Event.Filled(req))
//          ),
//          sequenceId = header.sequenceId
//        )
//        saved <- activityDal.saveRecord(record)
//      } yield saved

  }

}
