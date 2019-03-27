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

package io.lightcone.relayer.ethereummock

import akka.actor.Actor
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.lib._
import io.lightcone.relayer.data._

import scala.concurrent.ExecutionContext

class MockEthereumQueryActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout)
    extends Actor {

  def receive: Receive = {
    case req: GetAccount.Req =>
      sender ! queryProvider.getAccount(req)

    case req: GetFilledAmount.Req =>
      sender ! queryProvider.getFilledAmount(req)

    case req: GetOrderCancellation.Req =>
      sender ! queryProvider.getOrderCancellation(req)

    case req: GetCutoff.Req =>
      sender ! queryProvider.getCutoff(req)

    case req: BatchGetCutoffs.Req =>
      sender ! queryProvider.batchGetCutoffs(req)

    case req: GetBurnRate.Req =>
      sender ! queryProvider.getBurnRate(req)

    case req @ Notify("echo", _) =>
      sender ! req

    case req: GetNonce.Req =>
      sender ! queryProvider.getNonce(req)
  }
}
