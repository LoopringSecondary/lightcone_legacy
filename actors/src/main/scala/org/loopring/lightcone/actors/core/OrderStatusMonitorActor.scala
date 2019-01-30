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
import akka.cluster.singleton._
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.core.base.MetadataManager
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.proto._

import scala.concurrent._

// Owner: Hongyu
object OrderStatusMonitorActor {
  val name = "order_status_monitor"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      ma: ActorMaterializer,
      ece: ExecutionContextExecutor,
      metadataManager: MetadataManager,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new OrderStatusMonitorActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      name = OrderStatusMonitorActor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${OrderStatusMonitorActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${OrderStatusMonitorActor.name}_proxy"
    )
  }
}

class OrderStatusMonitorActor(
    val name: String = OrderStatusMonitorActor.name
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule,
    val metadataManager: MetadataManager)
    extends Actor
    with ActorLogging
    with NamedBasedConfig
    with RepeatedJobActor {

  import OrderStatus._

  val repeatedDelayInSeconds = selfConfig.getInt("delay-in-seconds")
  val activateLaggingInSecond = selfConfig.getInt("activate-lagging-seconds")
  val expireLeadInSeconds = selfConfig.getInt("expire-lead-seconds")
  val batchSize = selfConfig.getInt("batch-size")
  val initialDelayInSeconds = selfConfig.getInt("initial-dalay-in-seconds")

  val ACTIVATE_ORDER_NOTIFY = Notify("activate_order")
  val EXPIRE_ORDER_NOTIFY = Notify("expire_order")

  def mama = actors.get(MultiAccountManagerActor.name)
  def mma = actors.get(MarketManagerActor.name)

  val repeatedJobs = Seq(
    Job(
      name = "activate_order",
      dalayInSeconds = repeatedDelayInSeconds, // 1 minute
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => Future { self ! ACTIVATE_ORDER_NOTIFY }
    ),
    Job(
      name = "expire_order",
      dalayInSeconds = repeatedDelayInSeconds, // 1 minute
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => Future { self ! EXPIRE_ORDER_NOTIFY }
    )
  )

  def receive: Receive = super.receiveRepeatdJobs orElse {
    case ACTIVATE_ORDER_NOTIFY =>
      for {
        orders <- dbModule.orderService
          .getOrdersToActivate(activateLaggingInSecond, batchSize)

        _ <- Future.sequence(orders.map { o =>
          (mama ? ActorRecover.RecoverOrderReq(
            Some(o.copy(state = Some(o.getState.copy(status = STATUS_PENDING))))
          )).recover {
            case e: Exception =>
              log.error(
                s" occurs error:${e.getMessage}, ${e.printStackTrace}",
                " when submit an order that become active."
              )
          }
        })
        _ = if (orders.size >= batchSize) self ! ACTIVATE_ORDER_NOTIFY
      } yield orders.size

    case EXPIRE_ORDER_NOTIFY =>
      for {
        orders <- dbModule.orderService
          .getOrdersToExpire(expireLeadInSeconds, batchSize)

        _ <- Future.sequence(orders.map { o =>
          //只有是有效的市场订单才会发送该取消订单的数据，否则只会更改数据库状态
          if (!metadataManager
                .isMarketActiveOrReadOnly(MarketId(o.tokenS, o.tokenB))) {
            Future.unit
          } else {
            val cancelReq = CancelOrder.Req(
              o.hash,
              o.owner,
              STATUS_EXPIRED,
              Some(MarketId(o.tokenS, o.tokenB))
            )

            (mama ? cancelReq).recover {
              //发送到AccountManger失败后，会尝试发送个MarketManager,
              // 因为需要在AccountManger未启动的情况下通知到MarketManager
              case e: Exception =>
                log.error(
                  s" occurs error:${e.getMessage}, ${e.printStackTrace} ",
                  "when cancel an order that become expired."
                )
                mma ! cancelReq
            }
          }
        })

        _ = if (orders.size >= batchSize) self ! EXPIRE_ORDER_NOTIFY
      } yield orders.size
  }

}
