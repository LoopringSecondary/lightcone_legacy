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

package io.lightcone.relayer.actors

import akka.actor._
import akka.event.LoggingReceive
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import io.lightcone.relayer.base._
import io.lightcone.core._
import io.lightcone.ethereum.persistence.Fill
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer.data.GetRings.Req.Filter._
import io.lightcone.relayer.data._
import scala.concurrent._

// Owner: Yongfeng
object DatabaseQueryActor extends DeployedAsShardedEvenly {
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
    startSharding(Props(new DatabaseQueryActor()))
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
    extends InitializationRetryActor {
  val logger = Logger(this.getClass)
  val selfConfig = config.getConfig(DatabaseQueryActor.name)

  val getMarketFillsNum = selfConfig.getInt("get_market_fills_num")

  def ready: Receive = LoggingReceive {
    case req: GetOrders.Req =>
      val (tokensOpt, tokenbOpt, marketIdOpt) =
        getMarketQueryParameters(req.market)
      (for {
        result <- dbModule.orderService.getOrdersForUser(
          req.statuses.toSet,
          Some(req.owner),
          tokensOpt,
          tokenbOpt,
          marketIdOpt,
          None,
          Some(req.sort),
          req.skip
        )
        total <- dbModule.orderService.countOrdersForUser(
          req.statuses.toSet,
          Some(req.owner),
          tokensOpt,
          tokenbOpt,
          marketIdOpt,
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
            accountEntityId = 0,
            marketEntityId = 0
          )
        }
        GetOrders.Res(respOrder, total)
      }) sendTo sender

    case req: GetUserFills.Req =>
      (for {
        fills <- dbModule.fillDal.getFills(
          req.owner,
          req.txHash,
          req.orderHash,
          req.market,
          req.ring,
          req.wallet,
          req.miner,
          req.sort,
          req.paging
        )
        total <- dbModule.fillDal.countFills(
          req.owner,
          req.txHash,
          req.orderHash,
          req.market,
          req.ring,
          req.wallet,
          req.miner
        )
      } yield {
        val fills_ = fills.map { f =>
          new Fill(
            blockTimestamp = f.blockTimestamp,
            tokenS = f.tokenS,
            tokenB = f.tokenB,
            amountS = f.amountS,
            amountB = f.amountB,
            orderHash = f.orderHash,
            txHash = f.txHash
          )
        }
        GetUserFills.Res(fills_, total)
      }).sendTo(sender)

    case req: GetMarketFills.Req =>
      (for {
        fills <- dbModule.fillDal
          .getMarketFills(req.marketPair.get, getMarketFillsNum)
      } yield {
        val fills_ = fills.map { f =>
          new Fill(
            blockTimestamp = f.blockTimestamp,
            tokenS = f.tokenS,
            tokenB = f.tokenB,
            amountS = f.amountS,
            amountB = f.amountB,
            orderHash = f.orderHash,
            txHash = f.txHash
          )
        }
        GetMarketFills.Res(fills_)
      }).sendTo(sender)

    case req: GetRings.Req =>
      (for {
        _ <- Future.unit
        (ringHashOpt, ringIndexOpt) = req.filter match {
          case RingHash(h)  => (Some(h), None)
          case RingIndex(i) => (None, Some(i))
          case Empty        => (None, None)
        }
        result <- dbModule.ringDal
          .getRings(ringHashOpt, ringIndexOpt, req.sort, req.paging)
        total <- dbModule.ringDal.countRings(ringHashOpt, ringIndexOpt)
      } yield GetRings.Res(result, total)) sendTo sender
  }

  private def getMarketQueryParameters(marketOpt: Option[MarketFilter]) = {
    marketOpt match {
      case Some(m) =>
        m.direction match {
          case MarketFilter.Direction.BOTH =>
            (None, None, Some(MarketHash(m.marketPair.get).longId))
          case MarketFilter.Direction.BUY =>
            (
              Some(m.marketPair.get.quoteToken),
              Some(m.marketPair.get.baseToken),
              None
            )
          case MarketFilter.Direction.SELL =>
            (
              Some(m.marketPair.get.baseToken),
              Some(m.marketPair.get.quoteToken),
              None
            )

          case _ =>
            throw ErrorException(
              ErrorCode.ERR_INTERNAL_UNKNOWN,
              "unrecognized direction"
            )
        }
      case None => (None, None, None)
    }
  }

}
