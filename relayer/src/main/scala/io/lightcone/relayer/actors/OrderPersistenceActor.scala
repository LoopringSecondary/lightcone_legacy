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

import akka.actor._
import akka.cluster.sharding._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.core._

import io.lightcone.relayer.base._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.validator._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.core.ErrorCode._
import io.lightcone.proto._
import scala.concurrent._

// Owner: Yongfeng
object OrderPersistenceActor extends DeployedAsShardedEvenly {
  val name = "order_persistence"

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
    startSharding(Props(new OrderPersistenceActor()))
  }
}

class OrderPersistenceActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends InitializationRetryActor {
  import OrderStatus._

  //save order to db first, then send to AccountManager
  def ready: Receive = {
    case req: CancelOrder.Req =>
      (req.status match {
        case STATUS_SOFT_CANCELLED_BY_USER |
            STATUS_SOFT_CANCELLED_BY_USER_TRADING_PAIR =>
          for {
            cancelRes <- dbModule.orderService
              .cancelOrders(Seq(req.id), req.status)
          } yield {
            cancelRes.headOption match {
              case Some(res) =>
                if (res.order.isEmpty)
                  throw ErrorException(ERR_ORDER_NOT_EXIST, "no such order")
                CancelOrder.Res(ERR_NONE, req.status)
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
              case ERR_NONE => CancelOrder.Res(ERR_NONE, req.status) //取消成功
              case _        => throw ErrorException(cancelRes)
            }
      }) sendTo sender

    case SubmitOrder.Req(Some(raworder)) =>
      (for {
        saveRes <- dbModule.orderService.saveOrder(raworder)
      } yield {
        saveRes match {
          case Right(errCode) =>
            throw ErrorException(errCode, s"failed to submit order: $raworder")
          case Left(resRawOrder) =>
            resRawOrder
        }
      }) sendTo sender
  }
}
