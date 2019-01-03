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
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.ErrorCode._
import scala.concurrent.{ExecutionContext, Future}
import org.loopring.lightcone.persistence.DatabaseModule

object OrderCutoffHandlerActor {
  val name = "order_cutoff_handler"
}

class OrderCutoffHandlerActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(OrderCutoffHandlerActor.name)
    with ActorLogging {
  def mama = actors.get(MultiAccountManagerActor.name)
  val batchSize = selfConfig.getInt("batch-size")
  var numOrders = 0L
  var paging: CursorPaging = CursorPaging(size = batchSize)

  def receive: Receive = {

    case req: CutoffOrder.Req =>
      val now = timeProvider.getTimeSeconds()
      val cutoff = req.cutoff match {
        // TODO du:暂时不考虑broker
        /*
        case CutoffOrder.Req.Cutoff.ByBroker(value) =>
          if (value.broker.isEmpty)
            throw ErrorException(
              ErrorCode.ERR_INVALID_ARGUMENT,
              "broker could not be empty"
            )
          val cutoff = OrdersCutoffEvent(
            txHash = req.txHash,
            blockHeight = req.blockHeight,
            createdAt = now,
            broker = value.broker,
            cutoff = value.cutoff
          )
        case CutoffOrder.Req.Cutoff.ByBrokerPair(value) =>
          if (value.broker.isEmpty || value.tradingPair.isEmpty)
            throw ErrorException(
              ErrorCode.ERR_INVALID_ARGUMENT,
              "broker or tradingPair could not be empty"
            )
          val cutoff = OrdersCutoffEvent(
            txHash = req.txHash,
            blockHeight = req.blockHeight,
            createdAt = now,
            broker = value.broker,
            tradingPair = value.tradingPair,
            cutoff = value.cutoff
          )
         */
        case CutoffOrder.Req.Cutoff.ByOwner(value) =>
          if (value.broker.isEmpty)
            throw ErrorException(
              ErrorCode.ERR_INVALID_ARGUMENT,
              "owner could not be empty"
            )
          OrdersCutoffEvent(
            txHash = req.txHash,
            blockHeight = req.blockHeight,
            createdAt = now,
            broker = value.broker,
            owner = value.owner,
            cutoff = value.cutoff
          )

        case CutoffOrder.Req.Cutoff.ByOwnerPair(value) =>
          if (value.owner.isEmpty || value.tradingPair.isEmpty)
            throw ErrorException(
              ErrorCode.ERR_INVALID_ARGUMENT,
              "owner or tradingPair could not be empty"
            )
          OrdersCutoffEvent(
            txHash = req.txHash,
            blockHeight = req.blockHeight,
            createdAt = now,
            broker = value.broker,
            owner = value.broker,
            tradingPair = value.tradingPair,
            cutoff = value.cutoff
          )

        case _ =>
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"unhandled request: $req"
          )
      }
      for {
        saved <- dbModule.orderCutoffService.saveCutoff(cutoff)
        _ = if (saved != ERR_NONE) throw ErrorException(saved)
        // TODO du: 怎么保证一个事件被正确执行完？每个事件处理的地方都记录进度？
        _ = self ! BatchCutoffOrders.Req(Some(cutoff))
      } yield CutoffOrder.Res(ErrorCode.ERR_NONE)

    case req: BatchCutoffOrders.Req =>
      req.cutoff match {
        case Some(cutoff) =>
          dbModule.orderService.getCutoffAffectedOrders(cutoff, paging).map {
            r =>
              if (r.nonEmpty) {
                val cancelOrderReqs = r.map { o =>
                  CancelOrder.Req(
                    id = o.hash,
                    owner = o.owner,
                    status = OrderStatus.STATUS_CANCELLED_BY_USER
                  )
                }
                for {
                  notified <- Future.sequence(cancelOrderReqs.map(mama ? _))
                  updated <- dbModule.orderService.updateOrdersStatus(
                    r.map(_.hash),
                    OrderStatus.STATUS_CANCELLED_BY_USER
                  )
                  _ = if (updated != ERR_NONE)
                    throw ErrorException(ERR_INTERNAL_UNKNOWN, "update failed")
                } yield self ! req
              }
          }
          BatchCutoffOrders.Res()
        case None => BatchCutoffOrders.Res()
      }

    case m =>
      throw ErrorException(ERR_INTERNAL_UNKNOWN, s"unhandled message: $m")

  }
}
