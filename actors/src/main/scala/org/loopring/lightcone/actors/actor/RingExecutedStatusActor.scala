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

package org.loopring.lightcone.actors.actor

import akka.actor._
import akka.util.Timeout
import org.loopring.lightcone.proto.actors.RingExecutedRes
import org.loopring.lightcone.actors.routing.Routers
import org.loopring.lightcone.core.MarketId

import scala.concurrent.ExecutionContext

/** 监听ring的执行情况，
 *  1、执行状态发送给对应的marketmanager进行pendingpool的清除，
 *  2、记录执行状态并在订单与环路失败到一定次数后，永久删除订单
 *
 *  @param routes
 *  @param ec
 *  @param timeout
 */
class RingExecutedStatusActor()(
    implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor
  with ActorLogging {

  def receive: Receive = {
    case req: RingExecutedRes ⇒
      val marketIdOpt = req.ring.flatMap {
        r ⇒
          r.maker map {
            o ⇒
              val order = o.getOrder
              MarketId(order.tokenS, order.tokenB)
          }
      }
      marketIdOpt foreach {
        id ⇒
          val marketManagerActorOpt = Routers.getMarketManagingActor(id.toString)
          marketManagerActorOpt.foreach(_ ! req)
      }
  }
}
