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

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import akka.util.Timeout
import akka.pattern.pipe
import org.loopring.lightcone.actors.Routers
import org.loopring.lightcone.core.base.TokenValueEstimator
import org.loopring.lightcone.core.depth.OrderbookManager
import org.loopring.lightcone.core.market.MarketManager
import org.loopring.lightcone.proto.core.{GetXOrderbookReq, XOrderbookUpdate}

import scala.concurrent.ExecutionContext

object OrderbookManagerActor {
  def name = "orderbook_manager"
}

class OrderbookManagerActor(
  manager:OrderbookManager
)(
  implicit
  ec: ExecutionContext,
  timeout: Timeout,
)
  extends Actor
    with ActorLogging {

  def receive: Receive = LoggingReceive {
    case req: XOrderbookUpdate ⇒
      manager.processUpdate(req)
    case req: GetXOrderbookReq ⇒
      sender ! manager.getXOrderbook(req.level, req.size)
  }

}
