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
import akka.event.LoggingReceive
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.proto.XOrderStatusMonitor

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

  def processEffectiveOrders = {
    val processTime = timeProvider.getTimeSeconds()
    for {
      orders <- dbModule.orderService.getOrdersForMonitor()

    } yield {
      dbModule.orderStatusMonitorService.saveEvent(
        XOrderStatusMonitor(
          monitorType = XOrderStatusMonitor.XMonitorType.MONITOR_TYPE_EFFECTIVE,
          processTime = processTime
        )
      )
    }
  }

  def processExpireOrders = Future.successful()

}
