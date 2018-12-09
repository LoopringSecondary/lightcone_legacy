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
import akka.cluster.sharding._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.persistence._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core.XOrderStatus._
import org.loopring.lightcone.proto.core._
import scala.concurrent._

object OrderHistoryActor {
  val name = "order_history"

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ XGetBalanceAndAllowancesReq(address, _) ⇒ (address, msg)
    case msg @ XSubmitOrderReq(Some(xorder)) ⇒ ("address_1", msg) //todo:该数据结构并没有包含sharding信息，无法sharding
    case msg @ XStart(_) ⇒ ("address_1", msg) //todo:该数据结构并没有包含sharding信息，无法sharding
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case XGetBalanceAndAllowancesReq(address, _) ⇒ address
    case XSubmitOrderReq(Some(xorder)) ⇒ "address_1"
    case XStart(_) ⇒ "address_1"
  }

  def startShardRegion()(
    implicit system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef]): ActorRef = {
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new OrderHistoryActor()),
      settings = ClusterShardingSettings(system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId)
  }
}

class OrderHistoryActor()(
  implicit val config: Config,
  val ec: ExecutionContext,
  val timeProvider: TimeProvider,
  val timeout: Timeout,
  val actors: Lookup[ActorRef]) extends Actor with ActorLogging {

  val conf = config.getConfig(OrderHistoryActor.name)
  val thisConfig = try {
    conf.getConfig(self.path.name).withFallback(conf)
  } catch {
    case e: Throwable ⇒ conf
  }
  log.info(s"config for ${self.path.name} = $thisConfig")

  override def receive: Receive = {
    case XGetOrderFilledAmountReq ⇒ {

    }
  }
}
