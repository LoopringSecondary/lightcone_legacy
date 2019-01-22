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
import akka.cluster.singleton._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

import scala.concurrent._
import scala.util._

// Owner: Yadong
object EthereumEventExtractorActor {
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
      dispatchers: Seq[EventDispatcher[_]],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new EthereumEventExtractorActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      name = EthereumEventExtractorActor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${EthereumEventExtractorActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${EthereumEventExtractorActor.name}_proxy"
    )
  }
}

class EthereumEventExtractorActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val eventDispatchers: Seq[EventDispatcher[_]],
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(EthereumEventExtractorActor.name)
    with EventExtraction {

  var untilBlock: Long = Long.MaxValue //最大值，保证一直获取区块
  def gasPriceActor = actors.get(GasPriceActor.name)

  override def initialize(): Future[Unit] = {
    val startBlock = selfConfig.getLong("start-block")
    val f = for {
      lastHandledBlock: Option[Long] <- dbModule.blockService.findMaxHeight()
      currentBlock <- (ethereumAccessorActor ? GetBlockNumber.Req())
        .mapAs[GetBlockNumber.Res]
        .map(res => Numeric.toBigInt(res.result).longValue())
      blockStart = lastHandledBlock.getOrElse(startBlock)
      missing = currentBlock > blockStart + 1
      _ = if (missing) {
        dbModule.blockService.saveBlock(BlockData(height = currentBlock - 1))
        dbModule.missingBlocksRecordDal.saveMissingBlock(
          MissingBlocksRecord(blockStart, currentBlock, blockStart - 1)
        )
      }
    } yield {
      blockData = RawBlockData(height = currentBlock - 1)
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

  override def processEvents =
    super.processEvents.map(
      _ => {
        gasPriceActor ! BlockGasPrices(
          height = blockData.height,
          gasPrices = blockData.txs.map { tx =>
            Numeric.toBigInt(tx.gasPrice).longValue()
          }
        )
      }
    )

}
