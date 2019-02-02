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
import org.loopring.lightcone.core.base.MarketHash
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.GetOrdersForUser._
import org.loopring.lightcone.proto._
import scala.concurrent._

// Owner: Yongfeng
object DatabaseQueryActor extends ShardedEvenly {
  val name = "database_query"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new DatabaseQueryActor()),
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }
}

class DatabaseQueryActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(DatabaseQueryActor.name) {
  val logger = Logger(this.getClass)

  def ready: Receive = LoggingReceive {
    case req: GetOrdersForUser.Req =>
      val (tokensOpt, tokenbOpt, marketHashOpt) =
        getMarketQueryParameters(req.market)
      (for {
        result <- dbModule.orderService.getOrdersForUser(
          req.statuses.toSet,
          Some(req.owner),
          tokensOpt,
          tokenbOpt,
          marketHashOpt,
          None,
          Some(req.sort),
          req.skip
        )
        total <- dbModule.orderService.countOrdersForUser(
          req.statuses.toSet,
          Some(req.owner),
          tokensOpt,
          tokenbOpt,
          marketHashOpt,
          None
        )
      } yield {
        val respOrder = result.map { r =>
          val params = r.params match {
            case Some(o) => Some(o.copy(dualAuthPrivateKey = ""))
            case None    => None
          }
          r.copy(
            params = params,
            marketId = 0,
            accountShardEntity = "",
            marketShardEntity = ""
          )
        }
        GetOrdersForUser.Res(respOrder, total)
      }) sendTo sender

    case req: GetTrades.Req =>
      (for {
        result <- dbModule.tradeService.getTrades(req)
        total <- dbModule.tradeService.countTrades(req)
      } yield GetTrades.Res(result, total)) sendTo sender

    case req: GetRings.Req =>
      (for {
        result <- dbModule.ringService.getRings(req)
        total <- dbModule.ringService.countRings(req)
      } yield GetRings.Res(result, total)) sendTo sender
  }

  private def getMarketQueryParameters(marketOpt: Option[Req.Market]) = {
    marketOpt match {
      case Some(m)
          if m.tokenS.nonEmpty && m.tokenB.nonEmpty && m.isQueryBothSide =>
        (None, None, Some(MarketHash(MarketPair(m.tokenS, m.tokenB)).longId))
      case Some(m) if m.tokenS.nonEmpty && m.tokenB.nonEmpty =>
        (Some(m.tokenS), Some(m.tokenB), None)
      case Some(m) if m.tokenS.nonEmpty => (Some(m.tokenS), None, None)
      case Some(m) if m.tokenB.nonEmpty => (None, Some(m.tokenB), None)
      case None                         => (None, None, None)
    }
  }

}
