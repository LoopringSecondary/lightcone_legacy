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

package io.lightcone.relayer

import akka.actor.{Actor, ActorRef, Stash}
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.lib.TimeProvider
import io.lightcone.relayer.actors.KeepAliveActor
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.data._
import scala.concurrent.ExecutionContext

class MockEthereumAccessActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends Actor
    with Stash {

  def receive: Receive = {
    case node: NodeBlockHeight =>
    case msg: JsonRpc.Request  =>
    case req @ Notify(KeepAliveActor.NOTIFY_MSG, _) =>
      sender ! req

    case batchR: BatchCallContracts.Req =>
      val results = batchR.reqs.map { req =>
        DataPool.getResult(req).get.asInstanceOf[EthCall.Res]
      }
      sender ! BatchCallContracts.Res(results)

    case batchR: BatchGetTransactionReceipts.Req =>
      val results = batchR.reqs.map { req =>
        DataPool.getResult(req).get.asInstanceOf[GetTransactionReceipt.Res]
      }
      sender ! BatchGetTransactionReceipts.Res(results)

    case batchR: BatchGetTransactions.Req =>
      val results = batchR.reqs.map { req =>
        DataPool.getResult(req).get.asInstanceOf[GetTransactionByHash.Res]
      }
      sender ! BatchGetTransactions.Res(results)

    case batchR: BatchGetUncle.Req =>
      val results = batchR.reqs.map { req =>
        DataPool.getResult(req).get.asInstanceOf[GetBlockWithTxHashByHash.Res]
      }
      sender ! BatchGetUncle.Res(results)

    case batchR: BatchGetEthBalance.Req =>
      val results = batchR.reqs.map { req =>
        DataPool.getResult(req).get.asInstanceOf[EthGetBalance.Res]
      }
      sender ! BatchGetEthBalance.Res(results)

    case req @ Notify("init", _) =>
      sender ! req
    case req =>
      sender ! DataPool.getResult(req)

  }
}
