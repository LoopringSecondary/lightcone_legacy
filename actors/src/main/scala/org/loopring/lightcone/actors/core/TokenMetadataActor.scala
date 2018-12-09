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
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.persistence._
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core.XOrderStatus._
import org.loopring.lightcone.proto.core._
import scala.concurrent._

object TokenMetadataActor {
  val name = "token_metadata"

  def startShardRegion()(
    implicit
    system: ActorSystem,
    ec: ExecutionContext,
    actors: Lookup[ActorRef],
    timeProvider: TimeProvider,
    timeout: Timeout,
    dbModule: DatabaseModule
  ): ActorRef = {
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new TokenMetadataActor()),
      settings = ClusterShardingSettings(system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ XGetBalanceAndAllowancesReq(address, _) ⇒ (address, msg)
    case msg @ XSubmitOrderReq(Some(xorder)) ⇒ ("address_1", msg) //todo:该数据结构并没有包含sharding信息，无法sharding
    case msg @ XStart(_) ⇒ ("address_1", msg) //todo:该数据结构并没有包含sharding信息，无法sharding
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case XGetBalanceAndAllowancesReq(address, _) ⇒ address
    case XSubmitOrderReq(Some(xorder)) ⇒ "address_1"
    case XStart(_) ⇒ "address_1"
  }
}

class TokenMetadataActor()(
    implicit
    system: ActorSystem,
    ec: ExecutionContext,
    actors: Lookup[ActorRef],
    timeProvider: TimeProvider,
    timeout: Timeout,
    dbModule: DatabaseModule
) extends RepeatedJobActor
  with ActorLogging {
  private val tokenMetadata = dbModule.tokenMetadata
  val syncJob = Job(
    id = 1,
    name = "syncTokenValue",
    scheduleDelay = 10000,
    run = () ⇒ tokenMetadata.getTokens(true)
  )
  initAndStartNextRound(syncJob)

  override def receive: Receive = super.receive orElse LoggingReceive {
    case _ ⇒
  }
}
