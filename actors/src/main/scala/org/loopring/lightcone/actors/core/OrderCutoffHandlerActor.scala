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

object OrderCutoffHandlerActor {
  val name = "order_cutoff_handler"

  def startSingleton(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      dbModule: DatabaseModule,
      actors: Lookup[ActorRef]
    ): ActorRef = {
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new OrderCutoffHandlerActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(name)
      ),
      OrderCutoffHandlerActor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${OrderCutoffHandlerActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${OrderCutoffHandlerActor.name}_proxy"
    )
  }
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

  val batchSize =
    if (selfConfig.getInt("batch-size") > 0) selfConfig.getInt("batch-size")
    else 50

  def receive: Receive = {

    // TODO du: 收到任务后先存入db，一批处理完之后删除。
    // 如果执行失败，1. 自身重启时需要再恢复 2. 整体系统重启时直接删除不需要再恢复（accountManagerActor恢复时会处理cutoff）
    case req: OrdersCancelledEvent =>
      dbModule.orderService.getOrders(req.orderHashes).map(cancelOrders)

    case req: OwnerCutoffEvent =>
      if (req.owner.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Owner could not be empty"
        )
      log.info(s"Deal with cutoff:$req")
      self ! RetrieveOrdersToCancel(
        broker = req.broker,
        owner = req.owner,
        cutoff = req.cutoff
      )

    case req: OwnerTradingPairCutoffEvent =>
      if (req.owner.isEmpty || req.tradingPair.isEmpty)
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          "Owner or tradingPair could not be empty"
        )
      log.info(s"Deal with cutoff:$req")
      self ! RetrieveOrdersToCancel(
        broker = req.broker,
        owner = req.owner,
        tradingPair = req.tradingPair,
        cutoff = req.cutoff
      )

    case req: RetrieveOrdersToCancel =>
      dbModule.orderService.getCutoffAffectedOrders(req, batchSize).map { r =>
        if (r.nonEmpty) {
          log.info(
            s"Handle cutoff:$req in a batch:$batchSize request, return ${r.length} orders to cancel"
          )
          cancelOrders(r).map { _ =>
            self ! req
          }
        }
      }

    case m =>
      throw ErrorException(ERR_INTERNAL_UNKNOWN, s"Unhandled message: $m")
  }

  private def cancelOrders(orders: Seq[RawOrder]): Future[ErrorCode] = {
    val cancelOrderReqs = orders.map { o =>
      CancelOrder.Req(
        id = o.hash,
        owner = o.owner,
        status = OrderStatus.STATUS_CANCELLED_BY_USER,
        marketId = Some(MarketId(primary = o.tokenB, secondary = o.tokenS))
      )
    }
    for {
      notified <- Future.sequence(cancelOrderReqs.map(mama ? _))
      updated <- dbModule.orderService.updateOrdersStatus(
        orders.map(_.hash),
        OrderStatus.STATUS_CANCELLED_BY_USER
      )
      _ = if (updated != ERR_NONE)
        throw ErrorException(ERR_INTERNAL_UNKNOWN, "Update failed")
    } yield updated
  }
}
