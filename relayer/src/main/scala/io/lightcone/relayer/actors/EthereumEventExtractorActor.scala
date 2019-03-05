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
import io.lightcone.ethereum._
import io.lightcone.ethereum.extractor._
import io.lightcone.relayer.base._
import io.lightcone.relayer.ethereum._
import io.lightcone.lib._
import io.lightcone.persistence._
import io.lightcone.relayer.data
import io.lightcone.relayer.data._

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

  override def initialize(): Future[Unit] = {
    val startBlock = Math.max(selfConfig.getLong("start-block"), 0)
    val f = for {
      lastHandledBlock: Option[Long] <- dbModule.blockService.findMaxHeight()
      lastHandledBlockNum = lastHandledBlock.getOrElse(startBlock - 1)
      blockStartNum = Math.max(lastHandledBlockNum, startBlock - 1)
      blockStartData <- getBlockData(blockStartNum).map(_.get)
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

  def handleBlockReorganization: Receive = {
    case BLOCK_REORG_DETECTED =>
      for {
        dbBlock <- dbModule.blockDal.findByHeight(blockData.height - 1)
        onlineBlock <- getBlockData(blockData.height - 1)
      } yield {
        blockData = onlineBlock.get
        if (dbBlock.map(_.hash) == onlineBlock.map(_.hash))
          self ! GET_BLOCK
        else
          self ! BLOCK_REORG_DETECTED
      }
  }

}
