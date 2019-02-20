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

import com.corundumstudio.socketio._
import io.lightcone.relayer.socketio._
import com.google.inject.Inject
import io.lightcone.lib._

class TransactionNotifier @Inject()
    extends SocketIONotifier[SubscribeTransaction] {

  val eventName = "transactions"

  def wrapClient(
      client: SocketIOClient,
      req: SubscribeTransaction
    ) =
    new SocketIOSubscriber(
      client,
      req.copy(addresses = req.addresses.map(Address.normalize))
    )

  def shouldNotifyClient(
      request: SubscribeTransaction,
      event: AnyRef
    ): Boolean =
    event match {
      case e: TransactionResponse =>
        request.addresses.contains(e.owner) &&
          (request.types.isEmpty || request.types.contains(
            e.transaction.`type`
          ))
      case _ => false
    }
}