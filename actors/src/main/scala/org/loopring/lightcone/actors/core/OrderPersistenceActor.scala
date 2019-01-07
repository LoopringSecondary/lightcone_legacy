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
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.lib.{ErrorException, _}
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._
import scala.concurrent._

// main owner: 于红雨
object OrderPersistenceActor extends ShardedEvenly {
  val name = "order_handler"

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
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new OrderPersistenceActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      messageExtractor = messageExtractor
    )
  }
}

class OrderPersistenceActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(OrderPersistenceActor.name) {

  //save order to db first, then send to AccountManager
  def receive: Receive = {
    case req: CancelOrder.Req =>
      (req.status match {
        case OrderStatus.STATUS_CANCELLED_BY_USER =>
          for {
            cancelRes <- dbModule.orderService.markOrderSoftCancelled(
              Seq(req.id)
            )
          } yield {
            cancelRes.headOption match {
              case Some(res) =>
                if (res.order.isEmpty)
                  throw ErrorException(ERR_ORDER_NOT_EXIST, "no such order")
                CancelOrder.Res(req.id, req.status)
              case None =>
                throw ErrorException(ERR_ORDER_NOT_EXIST, "no such order")
            }
          }
        case _ =>
          for {
            cancelRes <- dbModule.orderService
              .updateOrderStatus(req.id, req.status)
          } yield
            cancelRes match {
              case ERR_NONE => CancelOrder.Res(req.id, req.status) //取消成功
              case _        => throw ErrorException(cancelRes)
            }
      }) sendTo sender

    case SubmitOrder.Req(Some(raworder)) =>
      (for {
        saveRes <- dbModule.orderService.saveOrder(raworder)
      } yield {
        saveRes match {
          case Right(errCode) =>
            throw ErrorException(
              errCode,
              s"failed to submit order: $raworder"
            )
          case Left(resRawOrder) =>
            resRawOrder
        }
      }) sendTo sender
  }
}
