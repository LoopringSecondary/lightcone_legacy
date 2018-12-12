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
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core.XOrderStatus._
import org.loopring.lightcone.proto.core._
import scala.concurrent._

// main owner: 于红雨
object OrderbookManagerActor extends ShardedByMarketId {
  val name = "orderbook_manager"

  def startShardRegion()(
    implicit
    system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef],
    tokenMetadataManager: TokenMetadataManager
  ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("instances-per-market")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new OrderbookManagerActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }

  // 如果message不包含一个有效的marketId，就不做处理，不要返回“默认值”
  val extractMarketId: PartialFunction[Any, String] = {
    case _ ⇒ ""
  }
}

class OrderbookManagerActor(
    extractEntityName: String ⇒ String = OrderbookManagerActor.extractEntityName
)(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val tokenMetadataManager: TokenMetadataManager
) extends ActorWithPathBasedConfig(
  OrderbookManagerActor.name,
  extractEntityName
) {
  val marketName = entityName

  // TODO(yongfeng): load marketconfig from database throught a service interface
  // based on marketName
  val xorderbookConfig = XMarketConfig(
    levels = selfConfig.getInt("levels"),
    priceDecimals = selfConfig.getInt("price-decimals"),
    precisionForAmount = selfConfig.getInt("precision-for-amount"),
    precisionForTotal = selfConfig.getInt("precision-for-total")
  )

  val manager: OrderbookManager = new OrderbookManagerImpl(xorderbookConfig)
  private var latestPrice: Option[Double] = None

  def receive: Receive = LoggingReceive {

    case XUpdateLatestTradingPrice(price) ⇒
      latestPrice = Some(price)

    case req: XOrderbookUpdate ⇒
      manager.processUpdate(req)

    case XGetOrderbookReq(level, size) ⇒
      sender ! manager.getOrderbook(level, size, latestPrice)
  }
}
