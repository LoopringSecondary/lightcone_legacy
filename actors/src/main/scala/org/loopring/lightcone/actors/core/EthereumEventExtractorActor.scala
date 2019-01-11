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
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

import scala.concurrent._
import scala.concurrent.duration._

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
      dispatchers: Seq[EventDispatcher[_, _]],
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
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dispatchers: Seq[EventDispatcher[_, _]],
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(EthereumEventExtractorActor.name) {

  var currentBlockNumber: BigInt = BigInt(-1)

  def ethereumAccessorActor: ActorRef = actors.get(EthereumAccessActor.name)

  def ethereumImplementActor: ActorRef =
    actors.get(EthereumEventImplementActor.name)

  override def preStart(): Unit = initial()

  override def preRestart(
      reason: Throwable,
      message: Option[Any]
    ) = {
    super.preRestart(reason, message)
    initial()
  }

  def initial() = {
    for {
      handledBlock: Option[Long] <- dbModule.blockService.findMaxHeight()
      maxBlock <- (ethereumAccessorActor ? GetBlockNumber.Req())
        .mapAs[GetBlockNumber.Res]
        .map(res => BigInt(Numeric.toBigInt(res.result)))
    } yield {
      currentBlockNumber = maxBlock - 1
      if (handledBlock.isDefined && handledBlock.get < maxBlock - 1) {
        ethereumImplementActor ! BlockImplementTask(
          handledBlock.get + 1 until maxBlock.longValue()
        )
      }
      self ! Notify("next")
    }
  }

  def receive: Receive = {
    case Notify("next", _) =>
      process()
  }

  def process(): Unit = {
    for {
      blockOpt <- (ethereumAccessorActor ? GetBlockWithTxObjectByNumber.Req(
        Numeric.toHexString((currentBlockNumber + 1).toByteArray)
      )).mapAs[GetBlockWithTxObjectByNumber.Res]
        .map(_.result)
      receipts <- if (blockOpt.isDefined) {
        (ethereumAccessorActor ? BatchGetTransactionReceipts.Req(
          blockOpt.get.transactions
            .map(tx => GetTransactionReceipt.Req(tx.hash))
        )).mapAs[BatchGetTransactionReceipts.Res]
          .map(_.resps.map(_.result))
      } else {
        Future.successful(Seq.empty)
      }
      uncles <- if (blockOpt.isDefined && blockOpt.get.uncles.nonEmpty) {
        val batchGetUnclesReq = BatchGetUncle.Req(
          blockOpt.get.uncles.zipWithIndex.unzip._2.map(
            index =>
              GetUncle.Req(
                blockOpt.get.number,
                Numeric.prependHexPrefix(index.toHexString)
              )
          )
        )
        (ethereumAccessorActor ? batchGetUnclesReq)
          .mapTo[BatchGetUncle.Res]
          .map(_.resps.map(_.result.get.miner))
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      if (blockOpt.isDefined) {
        if (receipts.forall(_.nonEmpty)) {
          val rawBlockData = RawBlockData(
            hash = blockOpt.get.hash,
            height = Numeric.toBigInt(blockOpt.get.number).longValue(),
            timestamp = blockOpt.get.timestamp,
            miner = blockOpt.get.miner,
            uncles = uncles,
            txs = blockOpt.get.transactions,
            receipts = receipts.map(_.get)
          )
          dispatchers.foreach(
            dispatcher => dispatcher.extractThenDispatchEvents(rawBlockData)
          )
          dbModule.blockService.saveBlock(
            BlockData(
              hash = rawBlockData.hash,
              height = rawBlockData.height,
              timestamp = Numeric.toBigInt(rawBlockData.timestamp).longValue()
            )
          )
          currentBlockNumber += 1
        } else {
          context.system.scheduler
            .scheduleOnce(1 seconds, self, Notify("next"))
        }
      } else {
        context.system.scheduler
          .scheduleOnce(15 seconds, self, Notify("next"))
      }
    }
  }

}
