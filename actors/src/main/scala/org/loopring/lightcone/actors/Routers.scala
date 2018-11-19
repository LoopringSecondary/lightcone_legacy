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

package org.loopring.lightcone.actors

import akka.actor.ActorRef
import org.loopring.lightcone.actors.core._

//todo:暂时简单实现
class Routers {
  var actors = Map.empty[String, ActorRef]

  def getActor(path: String): ActorRef = {
    actors(path)
  }
}

object Routers {

  def gasPriceProviderActor()(implicit routers: Routers): ActorRef = routers.getActor(s"/user/${GasPriceProviderActor.name}")
  def orderbookManagerActor()(implicit routers: Routers): ActorRef = routers.getActor(s"/user/${OrderbookManagerActor.name}")
  def marketManagerActor()(implicit routers: Routers): ActorRef = routers.getActor(s"/user/${MarketManagerActor.name}")
  //  def ethereumAccessActor()(implicit routers: Routers): ActorRef = routers.getActor("/user/ethereum_access")
  //  def ringSubmitterActor()(implicit routers: Routers): ActorRef = routers.getActor(s"/user/${RingSubmitterActor.name}")
  //  def tokenMetadataSyncActor()(implicit routers: Routers): ActorRef = routers.getActor(s"/user/${TokenMetadataSyncActor.name}")
}
