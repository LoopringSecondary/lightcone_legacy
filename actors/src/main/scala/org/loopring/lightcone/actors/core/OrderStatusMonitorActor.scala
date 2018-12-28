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

import scala.concurrent._

class OrderStatusMonitorActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends Actor
    with ActorLogging
    with RepeatedJobActor {

  val repeatedJobs = Seq(
    Job(
      name = "effective",
      dalayInSeconds = 60, // 1 minute
      run = () => processEffectiveOrders
    ),
    Job(
      name = "expire",
      dalayInSeconds = 60, // 1 minute
      run = () => processExpireOrders
    )
  )

  def processEffectiveOrders =
    for {
      (processTime, lastProcessTime) <- getProcessTime(
        XOrderStatusMonitor.XMonitorType.MONITOR_TYPE_EFFECTIVE
      )
      orders <- dbModule.orderService.getEffectiveOrdersForMonitor(
        lastProcessTime
      )
      _ <- Future.sequence(orders.map { o =>
        actors.get(MultiAccountManagerActor.name) ? XSubmitSimpleOrderReq(
          o.owner,
          Some(o)
        )
      })
    } yield {
      //记录处理时间
      dbModule.orderStatusMonitorService.saveEvent(
        XOrderStatusMonitor(
          monitorType = XOrderStatusMonitor.XMonitorType.MONITOR_TYPE_EFFECTIVE,
          processTime = processTime
        )
      )
    }

  def processExpireOrders =
    for {
      (processTime, lastProcessTime) <- getProcessTime(
        XOrderStatusMonitor.XMonitorType.MONITOR_TYPE_EXPIRE
      )
      orders <- dbModule.orderService.getExpiredOrdersForMonitor(processTime)
      _ <- Future.sequence(orders.map { o =>
        val cancelReq = XCancelOrderReq(
          o.hash,
          o.owner,
          o.status,
          Some(XMarketId(o.tokenS, o.tokenB))
        )
        actors.get(MultiAccountManagerActor.name) ? cancelReq
      })
    } yield {
      //记录处理时间
      dbModule.orderStatusMonitorService.saveEvent(
        XOrderStatusMonitor(
          monitorType = XOrderStatusMonitor.XMonitorType.MONITOR_TYPE_EXPIRE,
          processTime = processTime
        )
      )
    }

  private def getProcessTime(
      monitorType: XOrderStatusMonitor.XMonitorType
    ): Future[(Long, Long)] = {
    val processTime = timeProvider.getTimeSeconds()
    for {
      lastEventOpt <- dbModule.orderStatusMonitorService.getLastEvent(
        XOrderStatusMonitor.XMonitorType.MONITOR_TYPE_EXPIRE
      )
      lastProcessTime = if (lastEventOpt.isEmpty) 0
      else lastEventOpt.get.processTime
    } yield (processTime, lastProcessTime)
  }
}
