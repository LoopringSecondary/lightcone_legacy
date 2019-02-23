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

import akka.actor._
import io.lightcone.relayer.data.{GetNonce, SendRawTransaction}

class MockEthereumAccessActor(
    implicit
    val accessDataProvider: EthereumAccessDataProvider)
    extends Actor
    with ActorLogging {

  def receive: Receive = {
    case req: SendRawTransaction.Req =>
      sender ! accessDataProvider.sendRawTransaction(req)
    case req: GetNonce.Req =>
      sender ! GetNonce.Res()
    case msg =>
      log.info(s"received msg: ${msg}")
  }
}
