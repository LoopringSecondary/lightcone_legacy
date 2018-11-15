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

package org.loopring.lightcone.actors.routing

import akka.actor.ActorRef
import org.loopring.lightcone.actors.actor._
import org.loopring.lightcone.actors.managing.ClusterManager

object Routers extends RouterMap {
  // // Router for management actors
  def clusterManager: ActorRef = getRouterNamed(ClusterManager.name)

  // // Router for service actors
  def ethereumAccessActor: ActorRef = getRouterNamed(EthereumAccessActor.name)
  def marketManagingActor: ActorRef = getRouterNamed(MarketManagingActor.name)
  def orderFillHistoryActor: ActorRef = getRouterNamed(OrderFillHistoryActor.name)
  def orderManagingActor: ActorRef = getRouterNamed(OrderManagingActor.name)
  def ringSubmitActor: ActorRef = getRouterNamed(RingSubmitActor.name)
  def getMarketManagingActor(market: String): Option[ActorRef] = ???

}
