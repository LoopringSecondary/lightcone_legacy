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
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.proto._

import scala.annotation.tailrec
import scala.concurrent._

object OrderStatusMonitorActor {
  val name = "order_status_monitor"
}

class OrderStatusMonitorActor(
    val name: String = OrderStatusMonitorActor.name
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends Actor
    with ActorLogging
    with NamedBasedConfig
    with RepeatedJobActor {

  val repeatedDelayInSeconds = selfConfig.getInt("delay-in-seconds")
  val effectiveLagSeconds = selfConfig.getInt("effective-lag-seconds")
  val expiredLeadSeconds = selfConfig.getInt("expired-lead-seconds")
  val batchSize = selfConfig.getInt("batch-size")
  val maxRetriyCount = 500

  val repeatedJobs = Seq(
    Job(
      name = "effective",
      dalayInSeconds = repeatedDelayInSeconds, // 1 minute
      run = () => processEffectiveOrders
    ),
    Job(
      name = "expire",
      dalayInSeconds = repeatedDelayInSeconds, // 1 minute
      run = () => processExpireOrders
    )
  )

  private def processEffectiveOrdersPagging(lastProcessTime:Int, processTime:Int, skipOpt: Option[Paging] = None):Future[Unit] = for {
    orders <- dbModule.orderService.getEffectiveOrdersForMonitor(
      lastProcessTime,
      processTime,
      skipOpt
    )
    _ <- Future.sequence(orders.map { o =>
      actors.get(MultiAccountManagerActor.name) ? SubmitSimpleOrder(
        o.owner,
        Some(o.copy(state = Some(o.getState.copy(status = OrderStatus.STATUS_PENDING))))
      )
    })
    _ <- dbModule.orderService
      .updateOrdersStatus(orders.map(_.hash), OrderStatus.STATUS_PENDING)
    _ <- skipOpt match {
      case None => Future.successful(Unit)
      case Some(skip) =>
        if (orders.size >= skip.size && skip.skip/skip.size <= maxRetriyCount) {
        processEffectiveOrdersPagging(lastProcessTime, processTime, Some(skip.copy(skip = skip.skip + skip.size, size=skip.size)))
      } else {
        Future.successful(Unit)
      }
    }
  } yield Unit


  private def processExpiredOrdersPagging(lastProcessTime:Int, processTime:Int, skipOpt: Option[Paging] = None):Future[Unit] = for {
    orders <- dbModule.orderService
      .getExpiredOrdersForMonitor(
        lastProcessTime + expiredLeadSeconds,
        processTime + expiredLeadSeconds
      )
    _ <- Future.sequence(orders.map { o =>
      val cancelReq = CancelOrder.Req(
        o.hash,
        o.owner,
        OrderStatus.STATUS_EXPIRED,
        Some(MarketId(o.tokenS, o.tokenB))
      )
      (actors.get(MultiAccountManagerActor.name) ? cancelReq).recover {
        //发送到AccountManger失败后，会尝试发送个MarketManager, 因为需要在AccountManger未启动的情况下通知到MarketManager
        case e: Exception =>
          actors.get(MarketManagerActor.name) ? cancelReq
      }
    })
    //发送到AccountManager之后，更新状态到数据库
    _ <- dbModule.orderService
      .updateOrdersStatus(orders.map(_.hash), OrderStatus.STATUS_EXPIRED)
    _ <- skipOpt match {
      case None => Future.successful(Unit)
      case Some(skip) =>
        if (orders.size >= skip.size && skip.skip/skip.size <= maxRetriyCount) {
          processExpiredOrdersPagging(lastProcessTime, processTime, Some(skip.copy(skip = skip.skip + skip.size, size=skip.size)))
        } else {
          Future.successful(Unit)
        }
    }
  } yield Unit

  def processEffectiveOrders =
    for {
      (processTime, lastProcessTime) <- getProcessTime(
        OrderStatusMonitor.XMonitorType.MONITOR_TYPE_EFFECTIVE
      )
      _ <- processEffectiveOrdersPagging(lastProcessTime + effectiveLagSeconds, processTime + effectiveLagSeconds)
    } yield {
      //记录处理时间
      dbModule.orderStatusMonitorService.updateLastProcessingTimestamp(
        OrderStatusMonitor(
          monitorType = OrderStatusMonitor.XMonitorType.MONITOR_TYPE_EFFECTIVE,
          processTime = processTime
        )
      )
    }

  def processExpireOrders =
    for {
      (processTime, lastProcessTime) <- getProcessTime(
        OrderStatusMonitor.XMonitorType.MONITOR_TYPE_EXPIRE
      )
      _ <- processExpiredOrdersPagging(lastProcessTime + expiredLeadSeconds, processTime + expiredLeadSeconds)
    } yield {
      //记录处理时间
      dbModule.orderStatusMonitorService.updateLastProcessingTimestamp(
        OrderStatusMonitor(
          monitorType = OrderStatusMonitor.XMonitorType.MONITOR_TYPE_EXPIRE,
          processTime = processTime
        )
      )
    }

  private def getProcessTime(
      monitorType: OrderStatusMonitor.XMonitorType
    ): Future[(Int, Int)] = {
    val processTime = timeProvider.getTimeSeconds()
    for {
      lastEventOpt <- dbModule.orderStatusMonitorService
        .getLastProcessingTimestamp(
          monitorType
        )
      lastProcessTime = if (lastEventOpt.isEmpty) 0
      else lastEventOpt.get.processTime
    } yield (processTime.toInt, lastProcessTime.toInt)
  }
}
