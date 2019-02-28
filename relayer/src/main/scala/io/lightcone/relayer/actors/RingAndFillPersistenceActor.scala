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
import io.lightcone.ethereum._
import io.lightcone.ethereum.event._
import io.lightcone.relayer.base._
import io.lightcone.core._
import io.lightcone.ethereum.persistence._
import io.lightcone.lib._
import io.lightcone.persistence._
import scala.concurrent._

// Owner: Yongfeng
object RingAndFillPersistenceActor extends DeployedAsShardedEvenly {
  val name = "ring_and_fill_persistence"

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
    startSharding(Props(new RingAndFillPersistenceActor()))
  }
}

class RingAndFillPersistenceActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    dbModule: DatabaseModule)
    extends InitializationRetryActor {

  import ErrorCode._

  val selfConfig = config.getConfig(RingAndFillPersistenceActor.name)

  def ready: Receive = LoggingReceive {
    case e: RingMinedEvent =>
    //TODO:通知与保存分开，重构该部分
//      val header = e.header.getOrElse(EventHeader())
//      if (header.txStatus == TxStatus.TX_STATUS_SUCCESS) {
//        val fillAndFees = getFillsToPersist(e)
//        val ring = Ring(
//          e.ringHash,
//          e.ringIndex,
//          e.fills.length,
//          e.miner,
//          header.txHash,
//          Some(Ring.Fees(fillAndFees.map(_._1))),
//          header.getBlockHeader.height,
//          header.getBlockHeader.timestamp
//        )
//        for {
//          // TODO(du): 如果用事务需要在dal里注入dbModule
//          savedRing <- dbModule.ringService.saveRing(ring)
//          savedFills <- dbModule.fillService
//            .saveFills(fillAndFees.map(_._2))
//        } yield {
//          if (savedRing != ERR_NONE) {
//            log.error(s"ring and fills saved failed :$e")
//          }
//          if (savedFills.exists(_ != ERR_NONE)) {
//            log.error(s"fills saved failed :$e")
//          }
//        }
//      }
  }

  private def getFillsToPersist(e: RingMinedEvent) = {
//    val header = e.header.getOrElse(EventHeader())
//    e.fills.map { f =>
//      val fee = Fill.Fee(
//        f.tokenFee,
//        f.filledAmountFee,
//        f.feeAmountS,
//        f.feeAmountB,
//        e.feeRecipient,
//        f.waiveFeePercentage,
//        f.walletSplitPercentage
//      )
//      val fill = Fill(
//        f.owner,
//        f.orderHash,
//        f.ringHash,
//        f.ringIndex,
//        f.fillIndex,
//        header.txHash,
//        f.filledAmountS,
//        f.filledAmountB,
//        f.tokenS,
//        f.tokenB,
//        MarketHash(MarketPair(f.tokenS, f.tokenB)).longId(),
//        f.split,
//        Some(fee),
//        f.wallet,
//        e.miner,
//        header.getBlockHeader.height,
//        header.getBlockHeader.timestamp
//      )
//      (fee, fill)
//    }
  }

}
