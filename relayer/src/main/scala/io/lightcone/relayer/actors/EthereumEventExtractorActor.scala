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

import akka.actor.{Address => _, _}
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.ethereum.event.BlockEvent
import io.lightcone.ethereum.extractor._
import io.lightcone.lib._
import io.lightcone.persistence._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.ethereum._

import scala.concurrent._
import scala.util._

// Owner: Yadong
object EthereumEventExtractorActor extends DeployedAsSingleton {
  val name = "ethereum_event_extractor"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      eventDispatcher: EventDispatcher,
      eventExtractor: EventExtractor[BlockWithTxObject, AnyRef],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new EthereumEventExtractorActor()))
  }
}

class EthereumEventExtractorActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val eventDispatcher: EventDispatcher,
    val eventExtractor: EventExtractor[BlockWithTxObject, AnyRef],
    val dbModule: DatabaseModule)
    extends InitializationRetryActor
    with EventExtraction {

  val selfConfig = config.getConfig(EthereumEventExtractorActor.name)

  var untilBlock: Long = Long.MaxValue //最大值，保证一直获取区块

  @inline def ringAndFillPersistenceActor =
    actors.get(RingAndFillPersistenceActor.name)

  @inline def chainReorganizationManagerActor =
    actors.get(ChainReorganizationManagerActor.name)
  @inline def marketHistoryActor = actors.get(MarketHistoryActor.name)

  @inline def ringSettlementManagerActor =
    actors.get(RingSettlementManagerActor.name)

  override def initialize(): Future[Unit] = {
    val startBlock = Math.max(selfConfig.getLong("start-block"), 0)
    val f = for {
      lastHandledBlock: Option[Long] <- dbModule.blockService.findMaxHeight()
      lastHandledBlockNum = lastHandledBlock.getOrElse(startBlock - 1)
      blockStartNum = Math.max(lastHandledBlockNum, startBlock - 1)
      blockStartData <- {
        if (blockStartNum == -1)
          Future.successful(
            BlockWithTxObject(
              number = Some(NumericConversion.toAmount(BigInt(-1)))
            )
          )
        else getBlockData(blockStartNum).map(_.get)
      }
    } yield {
      blockData = blockStartData
    }
    f onComplete {
      case Success(value) =>
        becomeReady()
        self ! GET_BLOCK
      case Failure(e) =>
        throw e
    }
    f
  }

  def ready = handleMessage

  override def broadcastBlockEvent() = {
    val blockEvent = BlockEvent(
      blockNumber = NumericConversion.toBigInt(blockData.number).longValue(),
      txs = blockData.transactions.map(
        tx =>
          BlockEvent.Tx(
            from = Address.normalize(tx.from),
            nonce = NumericConversion.toBigInt(tx.nonce).toInt,
            txHash = tx.hash
          )
      )
    )

    ActivityActor.broadcast(blockEvent)
    //TODO: 如何发送，是否需要等待返回结果之后再发送其余的events，否则会导致数据不一致
    ringAndFillPersistenceActor ! blockEvent
    chainReorganizationManagerActor ! blockEvent
    marketHistoryActor ! blockEvent
    ringSettlementManagerActor ! blockEvent
  }

  def handleBlockReorganization: Receive = {
    case BLOCK_REORG_DETECTED =>
      for {
        dbBlock <- dbModule.blockDal.findByHeight(
          NumericConversion.toBigInt(blockData.number).longValue() - 1
        )
        onlineBlock <- getBlockData(blockData.number)
      } yield {
        blockData = onlineBlock.get
        if (dbBlock.map(_.hash) == onlineBlock.map(_.hash))
          self ! GET_BLOCK
        else
          self ! BLOCK_REORG_DETECTED
      }
  }

}
