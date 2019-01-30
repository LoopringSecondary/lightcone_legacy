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
import org.loopring.lightcone.actors.data._
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
  import OrderStatusMonitor.MonitoringType._
  import OrderStatus._

  val repeatedDelayInSeconds = selfConfig.getInt("delay-in-seconds")
  val activateLaggingInSecond = selfConfig.getInt("activate-lagging-seconds")
  val expireLeadInSeconds = selfConfig.getInt("expire-lead-seconds")
  val batchSize = selfConfig.getInt("batch-size")
  val initialDelayInSeconds = selfConfig.getInt("initial-dalay-in-seconds")
  val maxRetriesCount = 500

  val repeatedJobs = Seq(
    Job(
      name = "activate_order",
      dalayInSeconds = repeatedDelayInSeconds, // 1 minute
      initialDalayInSeconds = initialDelayInSeconds,
      run = () =>
        runJob(
          processFunction = activateOrders,
          skipOpt = Some(Paging(0, batchSize)),
          monitoringType = MONITORING_ACTIVATE,
          leadOrLagSeconds = activateLaggingInSecond
        )
    ),
    Job(
      name = "expire_order",
      dalayInSeconds = repeatedDelayInSeconds, // 1 minute
      initialDalayInSeconds = initialDelayInSeconds,
      run = () =>
        runJob(
          processFunction = expireOrders,
          skipOpt = Some(Paging(0, batchSize)),
          monitoringType = MONITORING_EXPIRE,
          leadOrLagSeconds = expireLeadInSeconds
        )
    )
  )

  def receive = super.receiveRepeatdJobs

  def runJob(
      processFunction: (Int, Int, Option[Paging]) => Future[Int],
      latestProcessTimeOpt: Option[Int] = None,
      processTimeOpt: Option[Int] = None,
      skipOpt: Option[Paging] = None,
      monitoringType: OrderStatusMonitor.MonitoringType,
      leadOrLagSeconds: Int
    ): Future[Unit] = {
    for {
      (latestProcessTime, processTime) <- if (processTimeOpt.isEmpty)
        getProcessTime(monitoringType, leadOrLagSeconds)
      else
        Future.successful(latestProcessTimeOpt.get, processTimeOpt.get)
      orderSize <- processFunction(latestProcessTime, processTime, skipOpt)
      _ = log.debug(s"latestProcessTime: ${latestProcessTime}, ${processTime}")
      _ <- skipOpt match {
        case None => //记录本次处理时间
          dbModule.orderStatusMonitorService.updateLatestProcessingTime(
            OrderStatusMonitor(
              monitoringType = monitoringType.name,
              processTime = processTime
            )
          )

        case Some(skip) =>
          if (orderSize >= skip.size && skip.skip / skip.size <= maxRetriesCount) {
            runJob(
              processFunction,
              Some(latestProcessTime),
              Some(processTime),
              Some(skip.copy(skip = skip.skip + skip.size, size = skip.size)),
              monitoringType,
              leadOrLagSeconds
            )
          } else {
            //记录本次处理时间
            dbModule.orderStatusMonitorService.updateLatestProcessingTime(
              OrderStatusMonitor(
                monitoringType = monitoringType.name,
                processTime = processTime
              )
            )
          }
      }
    } yield Unit
  }

  private def activateOrders(
      latestProcessTime: Int,
      processTime: Int,
      skipOpt: Option[Paging] = None
    ): Future[Int] =
    for {
      orders <- dbModule.orderService.getOrdersToActivate(
        latestProcessTime,
        processTime,
        skipOpt
      )
      _ <- Future.sequence(orders.map { o =>
        actors
          .get(MultiAccountManagerActor.name) ? ActorRecover.RecoverOrderReq(
          Some(o.withStatus(STATUS_PENDING))
        )
      })
      //还是需要更新数据库的
      _ <- dbModule.orderService
        .updateOrdersStatus(orders.map(_.hash), STATUS_PENDING)
    } yield orders.size

  private def expireOrders(
      latestProcessTime: Int,
      processTime: Int,
      skipOpt: Option[Paging] = None
    ): Future[Int] =
    for {
      orders <- dbModule.orderService
        .getOrdersToExpire(latestProcessTime, processTime)
      _ <- Future.sequence(orders.map { o =>
        //只有是有效的市场订单才会发送该取消订单的数据，否则只会更改数据库状态
        // TODO:review时，其他的修改，在另一个pr提交
        if (metadataManager
              .isValidMarket(MarketId(o.tokenS, o.tokenB))) {
          val cancelReq = CancelOrder.Req(
            o.hash,
            o.owner,
            STATUS_EXPIRED,
            Some(MarketId(o.tokenS, o.tokenB))
          )
          (actors.get(MultiAccountManagerActor.name) ? cancelReq).recover {
            //发送到AccountManger失败后，会尝试发送个MarketManager, 因为需要在AccountManger未启动的情况下通知到MarketManager
            case e: Exception =>
              actors.get(MarketManagerActor.name) ? cancelReq
          }
        } else {
          Future.unit
        }
      })
      //发送到AccountManager之后，更新状态到数据库
      _ <- dbModule.orderService
        .updateOrdersStatus(orders.map(_.hash), STATUS_EXPIRED)
    } yield orders.size

  private def getProcessTime(
      monitoringType: OrderStatusMonitor.MonitoringType,
      leadOrLagSeconds: Int
    ): Future[(Int, Int)] = {
    val processTime = timeProvider.getTimeSeconds()
    for {
      lastEventOpt <- dbModule.orderStatusMonitorService
        .getLatestProcessingTime(monitoringType.name)
      latestProcessTime = if (lastEventOpt.isEmpty) 0
      else lastEventOpt.get.processTime
    } yield
      (
        latestProcessTime.toInt + leadOrLagSeconds,
        processTime.toInt + leadOrLagSeconds
      )
  }
}
