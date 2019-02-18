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

package io.lightcone.relayer.socketio.notifiers

import akka.actor.ActorSystem
import com.corundumstudio.socketio._
import io.lightcone.relayer.socketio._
import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.lib.Address

import scala.concurrent.ExecutionContext

class BalanceNotifier @Inject()(
    implicit
    val system: ActorSystem,
    val ec: ExecutionContext,
    val config: Config)
    extends SocketIONotifier[SubscribeBalanceAndAllowance] {

  val eventName = "balances"

  def wrapClient(
      client: SocketIOClient,
      req: SubscribeBalanceAndAllowance
    ) =
    new SocketIOSubscriber(
      client,
      req.copy(
        addresses = req.addresses.map(Address.normalize),
        tokens = req.tokens.map(Address.normalize)
      )
    )

  def shouldNotifyClient(
      request: SubscribeBalanceAndAllowance,
      event: AnyRef
    ): Boolean =
    event match {
      case e: BalanceAndAllowanceResponse =>
        request.addresses.contains(e.owner) && (request.tokens.isEmpty || request.tokens
          .contains(e.balanceAndAllowance.address))
      case _ => false
    }
}
