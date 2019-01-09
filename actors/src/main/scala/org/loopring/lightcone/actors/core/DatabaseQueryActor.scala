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
import com.typesafe.scalalogging.Logger
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import scala.concurrent._

// main owner: 杜永丰
object DatabaseQueryActor extends ShardedEvenly {
  val name = "database_query"

  def startShardRegion(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new DatabaseQueryActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      messageExtractor = messageExtractor
    )
  }
}

class DatabaseQueryActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(DatabaseQueryActor.name) {
  val logger = Logger(this.getClass)

  def receive: Receive = LoggingReceive {
    case req: GetOrdersForUser.Req =>
      (for {
        result <- req.market match {
          case GetOrdersForUser.Req.Market.Empty =>
            dbModule.orderService.getOrdersForUser(
              req.statuses.toSet,
              Some(req.owner),
              None,
              None,
              None,
              None,
              Some(req.sort),
              req.skip
            )
          case GetOrdersForUser.Req.Market.MarketHash(value) =>
            dbModule.orderService.getOrdersForUser(
              req.statuses.toSet,
              Some(req.owner),
              None,
              None,
              Some(value),
              None,
              Some(req.sort),
              req.skip
            )
          case GetOrdersForUser.Req.Market.Pair(value) =>
            dbModule.orderService.getOrdersForUser(
              req.statuses.toSet,
              Some(req.owner),
              Some(value.tokenS),
              Some(value.tokenB),
              None,
              None,
              Some(req.sort),
              req.skip
            )
        }
      } yield GetOrdersForUser.Res(result, ErrorCode.ERR_NONE)) sendTo sender
    case req: GetTrades.Req =>
      (for {
        result <- dbModule.tradeService.getTrades(req)
      } yield GetTrades.Res(result)) sendTo sender
    case m => logger.error(s"Unhandled message ${m}")
  }

}
