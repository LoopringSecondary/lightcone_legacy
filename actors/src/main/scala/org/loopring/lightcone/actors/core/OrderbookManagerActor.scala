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
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto._

import scala.collection.JavaConverters._
import scala.concurrent._

// main owner: 于红雨
object OrderbookManagerActor extends ShardedByMarket {
  val name = "orderbook_manager"

  def startShardRegion(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      tokenMetadataManager: TokenMetadataManager
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("instances-per-market")

    val markets = config
      .getObjectList("markets")
      .asScala
      .map { item =>
        val c = item.toConfig
        val marketId =
          XMarketId(c.getString("priamry"), c.getString("secondary"))
        OrderbookManagerActor.getEntityId(marketId) -> marketId
      }
      .toMap

    println(s"### orderbook ClusterSharding ${markets}")
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new OrderbookManagerActor(markets)),
      settings = ClusterShardingSettings(system).withRole(name),
      messageExtractor = messageExtractor
    )
  }

  // 如果message不包含一个有效的marketId，就不做处理，不要返回“默认值”
  val extractMarketId: PartialFunction[Any, XMarketId] = {
    case XGetOrderbook(_, _, Some(marketId))          => marketId
    case XOrderbookUpdate(_, _, Some(marketId))       => marketId
    case XUpdateLatestTradingPrice(_, Some(marketId)) => marketId
  }
}

class OrderbookManagerActor(
    markets: Map[String, XMarketId],
    extractEntityName: String => String =
      OrderbookManagerActor.extractEntityName
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val tokenMetadataManager: TokenMetadataManager)
    extends ActorWithPathBasedConfig(
      OrderbookManagerActor.name,
      extractEntityName
    ) {
  val marketId = markets(entityName)
  val marketIdHashedValue = OrderbookManagerActor.getEntityId(marketId)

  // TODO(yongfeng): load marketconfig from database throught a service interface
  // based on marketId
  val xorderbookConfig = XMarketConfig(
    levels = selfConfig.getInt("levels"),
    priceDecimals = selfConfig.getInt("price-decimals"),
    precisionForAmount = selfConfig.getInt("precision-for-amount"),
    precisionForTotal = selfConfig.getInt("precision-for-total")
  )

  val manager: OrderbookManager = new OrderbookManagerImpl(xorderbookConfig)
  private var latestPrice: Option[Double] = None

  def receive: Receive = LoggingReceive {

    case XUpdateLatestTradingPrice(price, _) =>
      log.info(s"receive XUpdateLatestTradingPrice ${price}")
      latestPrice = Some(price)

    case req: XOrderbookUpdate =>
      log.info(s"receive XOrderbookUpdate ${req}")
      manager.processUpdate(req)

    case XGetOrderbook(level, size, Some(marketId)) =>
      Future {
        if (OrderbookManagerActor.getEntityId(marketId) == marketIdHashedValue)
          manager.getOrderbook(level, size, latestPrice)
        else
          throw ErrorException(
            XErrorCode.ERR_INVALID_ARGUMENT,
            s"marketId doesn't match, expect: ${marketId} ,receive: ${marketId}"
          )
      } sendTo sender
    case msg => log.info(s"not supported msg:${msg}, ${marketId}")

  }
}
