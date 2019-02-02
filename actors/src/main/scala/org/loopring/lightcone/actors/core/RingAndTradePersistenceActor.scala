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
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.event.LoggingReceive
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.core.base.MarketHash
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.ErrorCode._
import scala.concurrent._

// Owner: Yongfeng
object RingAndTradePersistenceActor extends ShardedEvenly {
  val name = "ring_and_trade_persistence"

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
      entityProps = Props(new RingAndTradePersistenceActor()),
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }
}

class RingAndTradePersistenceActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(RingAndTradePersistenceActor.name) {

  def ready: Receive = LoggingReceive {
    case e: RingMinedEvent =>
      val header = e.header.getOrElse(EventHeader())
      if (header.txStatus == TxStatus.TX_STATUS_SUCCESS) {
        val tradeAndFees = getTradesToPersist(e)
        val ring = Ring(
          e.ringHash,
          e.ringIndex,
          e.fills.length,
          e.miner,
          header.txHash,
          Some(Ring.Fees(tradeAndFees.map(_._1))),
          header.blockNumber,
          header.blockTimestamp
        )
        for {
          // TODO(du): 如果用事务需要在dal里注入dbModule
          savedRing <- dbModule.ringService.saveRing(ring)
          savedTrades <- dbModule.tradeService
            .saveTrades(tradeAndFees.map(_._2))
        } yield {
          if (savedRing != ERR_NONE) {
            log.error(s"ring and trades saved failed :$e")
          }
          if (savedTrades.exists(_ != ERR_NONE)) {
            log.error(s"trades saved failed :$e")
          }
        }
      }
  }

  private def getTradesToPersist(e: RingMinedEvent) = {
    val header = e.header.getOrElse(EventHeader())
    e.fills.map { f =>
      val fee = Trade.Fee(
        f.tokenFee,
        f.filledAmountFee,
        f.feeAmountS,
        f.feeAmountB,
        e.feeRecipient,
        f.waiveFeePercentage,
        f.walletSplitPercentage
      )
      val trade = Trade(
        f.owner,
        f.orderHash,
        f.ringHash,
        f.ringIndex,
        f.fillIndex,
        header.txHash,
        f.filledAmountS,
        f.filledAmountB,
        f.tokenS,
        f.tokenB,
        MarketHash(f.tokenS, f.tokenB).toString,
        f.split,
        Some(fee),
        f.wallet,
        e.miner,
        header.blockNumber,
        header.blockTimestamp
      )
      (fee, trade)
    }
  }

}
