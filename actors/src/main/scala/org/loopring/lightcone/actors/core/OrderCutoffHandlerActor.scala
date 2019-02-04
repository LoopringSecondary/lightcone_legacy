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
import akka.cluster.singleton._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.ErrorCode._
import scala.concurrent.{ExecutionContext, Future}
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.actors.base.safefuture._

// Owner: Yongfeng
object OrderCutoffHandlerActor extends DeployedAsSingleton {
  val name = "order_cutoff_handler"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      dbModule: DatabaseModule,
      actors: Lookup[ActorRef],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new OrderCutoffHandlerActor()))
  }
}

// TODO(yongfeng):需要验证当一个市场被禁用后，应该能正常更新数据库，并且不影响后续执行
class OrderCutoffHandlerActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends InitializationRetryActor
    with ActorLogging {
  import OrderStatus._

  val selfConfig = config.getConfig(OrderCutoffHandlerActor.name)
  def mama = actors.get(MultiAccountManagerActor.name)
  val batchSize = selfConfig.getInt("batch-size")

  def ready: Receive = {

    // TODO du: 收到任务后先存入db，一批处理完之后删除。
    // 如果执行失败，1. 自身重启时需要再恢复 2. 整体系统重启时直接删除不需要再恢复（accountManagerActor恢复时会处理cutoff）
    case req: OrdersCancelledEvent =>
      dbModule.orderService
        .getOrders(req.orderHashes)
        .map(cancelOrders(_, STATUS_ONCHAIN_CANCELLED_BY_USER))
        .sendTo(sender)

    case req: CutoffEvent =>
      if (req.owner.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "owner in CutoffEvent is empty"
        )
      log.debug(s"Deal with cutoff:$req")
      self ! RetrieveOrdersToCancel(
        broker = req.broker,
        owner = req.owner,
        cutoff = req.cutoff
      )

    case req: RetrieveOrdersToCancel =>
      val cancelStatus = if (req.marketHash.nonEmpty) {
        STATUS_ONCHAIN_CANCELLED_BY_USER_TRADING_PAIR
      } else {
        STATUS_ONCHAIN_CANCELLED_BY_USER
      }
      for {
        affectOrders <- dbModule.orderService
          .getCutoffAffectedOrders(req, batchSize)
        _ = log.debug(
          s"Handle cutoff:$req in a batch:$batchSize request, return ${affectOrders.length} orders to cancel"
        )
        _ <- cancelOrders(affectOrders, cancelStatus)
      } yield if (affectOrders.nonEmpty) self ! req

  }

  private def cancelOrders(
      orders: Seq[RawOrder],
      status: OrderStatus
    ): Future[Unit] = {
    val cancelOrderReqs = orders.map { o =>
      CancelOrder.Req(
        id = o.hash,
        owner = o.owner,
        status = status,
        marketPair = Some(MarketPair(o.tokenB, o.tokenS))
      )
    }
    for {
      notified <- Future.sequence(cancelOrderReqs.map(mama ? _))
      updated <- dbModule.orderService
        .updateOrdersStatus(orders.map(_.hash), status)
      _ = if (updated != ERR_NONE)
        throw ErrorException(ERR_INTERNAL_UNKNOWN, "Update order status failed")
    } yield Unit
  }
}
