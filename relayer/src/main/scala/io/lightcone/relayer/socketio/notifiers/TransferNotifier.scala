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

import com.corundumstudio.socketio.SocketIOClient
import com.google.inject.Inject
import io.lightcone.lib.Address
import io.lightcone.relayer.socketio._

class TransferNotifier @Inject() extends SocketIONotifier[SubscribeTransfer] {

  val eventName: String = "transfers"

  def wrapClient(
      client: SocketIOClient,
      subscription: SubscribeTransfer
    ): SocketIOSubscriber[SubscribeTransfer] =
    new SocketIOSubscriber[SubscribeTransfer](
      client,
      subscription = subscription.copy(
        address = Address.normalize(subscription.address),
        tokens = subscription.tokens.map(Address.normalize)
      )
    )

  def shouldNotifyClient(
      subscription: SubscribeTransfer,
      event: AnyRef
    ): Boolean = {
    event match {
      case transfer: Transfer =>
        subscription.address == transfer.owner &&
          subscription.tokens.contains(transfer.token) &&
          (subscription.`type`.isEmpty ||
            (subscription.`type` == "income" && transfer.to == transfer.owner) ||
            (subscription.`type` == "outcome" && transfer.from == transfer.owner))

      case _ => false
    }
  }

}
