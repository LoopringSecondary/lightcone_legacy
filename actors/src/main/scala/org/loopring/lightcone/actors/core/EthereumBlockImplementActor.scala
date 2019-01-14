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
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object EthereumBlockImplementActor {
  val name = "ethereum_event_implement"

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
        singletonProps = Props(new EthereumBlockImplementActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      name = EthereumBlockImplementActor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${EthereumBlockImplementActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${EthereumBlockImplementActor.name}_proxy"
    )
  }

}

class EthereumBlockImplementActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    dispatchers: Seq[EventDispatcher[_]],
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(EthereumBlockImplementActor.name) {

  val taskQueue = new mutable.Queue[Long]()
  var currentBlockNumber = 0L
  def ethereumAccessorActor: ActorRef = actors.get(EthereumAccessActor.name)

  override def receive: Receive = {
    case Notify("next", _) =>
      if (taskQueue.nonEmpty) {
        currentBlockNumber = taskQueue.dequeue()
        process()
      } else {
        context.system.scheduler.scheduleOnce(30 seconds, self, Notify("next"))
      }
    case Notify("current", _) =>
      process()
    case BlockImplementTask(blocks) =>
      taskQueue.enqueue(blocks: _*)
  }

  def process(): Unit = {
    for {
      block <- (ethereumAccessorActor ? GetBlockWithTxObjectByNumber.Req(
        Numeric.toHexString(BigInt(currentBlockNumber).toByteArray)
      )).mapAs[GetBlockWithTxObjectByNumber.Res]
        .map(_.result.get)
      receipts <- (ethereumAccessorActor ? BatchGetTransactionReceipts.Req(
        block.transactions
          .map(tx => GetTransactionReceipt.Req(tx.hash))
      )).mapAs[BatchGetTransactionReceipts.Res]
        .map(_.resps.map(_.result))
      uncles <- if (block.uncles.nonEmpty) {
        val batchGetUnclesReq = BatchGetUncle.Req(
          block.uncles.indices.map(
            index =>
              GetUncle
                .Req(block.number, Numeric.prependHexPrefix(index.toHexString))
          )
        )
        (ethereumAccessorActor ? batchGetUnclesReq)
          .mapTo[BatchGetUncle.Res]
          .map(_.resps.map(_.result.get.miner))
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      if (receipts.forall(_.nonEmpty)) {
        val rawBlockData = RawBlockData(
          hash = block.hash,
          height = Numeric.toBigInt(block.number).longValue(),
          timestamp = block.timestamp,
          miner = block.miner,
          uncles = uncles,
          txs = block.transactions,
          receipts = receipts.map(_.get)
        )
        dispatchers.foreach(_.dispatch(rawBlockData))
        dbModule.blockService.saveBlock(
          BlockData(
            hash = rawBlockData.hash,
            height = rawBlockData.height,
            timestamp = Numeric.toBigInt(rawBlockData.timestamp).longValue()
          )
        )
      } else {
        context.system.scheduler
          .scheduleOnce(1 seconds, self, Notify("current"))
      }
    }
  }
}
