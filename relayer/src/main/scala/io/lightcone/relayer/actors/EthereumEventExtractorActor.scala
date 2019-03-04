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
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.ethereum.BlockData
import io.lightcone.lib._
import io.lightcone.persistence._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.ethereum._
import io.lightcone.relayer.ethereum.event._

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
      eventExtractor: EventExtractor,
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
    val eventExtractor: EventExtractor,
    val dbModule: DatabaseModule)
    extends InitializationRetryActor
    with EventExtraction {

  val selfConfig = config.getConfig(EthereumEventExtractorActor.name)

  val preBlockNumber = selfConfig.getInt("pre-block")

  var untilBlock: Long = Long.MaxValue //最大值，保证一直获取区块

  override def initialize(): Future[Unit] = {
    val startBlock = Math.max(selfConfig.getLong("start-block"), 0)
    val f = for {
      lastHandledBlock: Option[Long] <- dbModule.blockService.findMaxHeight()
      currentBlock <- (ethereumAccessorActor ? GetBlockNumber.Req())
        .mapAs[GetBlockNumber.Res]
        .map(res => NumericConversion.toBigInt(res.result).longValue)
      lastHandledBlockNum = lastHandledBlock.getOrElse(startBlock - 1)
      preBlockNum = Math.max(currentBlock - 1 - preBlockNumber, 0)
      missing = preBlockNum > lastHandledBlockNum
      preBlockData <- getBlockData(preBlockNum).map(_.get)
      lastHandledBlockData <- dbModule.blockDal.findByHeight(
        lastHandledBlockNum
      )
      startBlockData <- getBlockData(startBlock).map(_.get)
      _ = if (missing) {
        dbModule.blockService.saveBlock(
          BlockData(hash = preBlockData.hash, height = preBlockData.height)
        )
        dbModule.missingBlocksRecordDal.saveMissingBlock(
          MissingBlocksRecord(
            lastHandledBlockNum + 1,
            currentBlock,
            lastHandledBlockNum
          )
        )
      }
    } yield {
      blockData =
        if (missing) preBlockData
        else
          lastHandledBlockData
            .map(data => RawBlockData(hash = data.hash, height = data.height))
            .getOrElse(startBlockData)
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

  def handleFork: Receive = {
    case DETECT_FORK_HEIGHT =>
      for {
        dbBlock <- dbModule.blockDal.findByHeight(blockData.height - 1)
        onlineBlock <- getBlockData(blockData.height - 1)
      } yield {
        blockData = onlineBlock.get
        if (dbBlock.get.hash == onlineBlock.get.hash)
          self ! GET_BLOCK
        else
          self ! DETECT_FORK_HEIGHT
      }
  }

}
