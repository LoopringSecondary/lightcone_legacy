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
import com.google.inject.Inject
import io.lightcone.ethereum.event.AddressBalanceAndAllowanceEvent
import io.lightcone.lib.Address
import io.lightcone.relayer.socketio._
import io.lightcone.core._

class BalanceNotifier @Inject()
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

  def extractNotifyData(
      subscription: SubscribeBalanceAndAllowance,
      event: AnyRef
    ): Option[AnyRef] = {
    event match {
      case e: AddressBalanceAndAllowanceEvent =>
        if (subscription.addresses.contains(e.address)
            && (subscription.tokens.isEmpty || subscription.tokens.contains(
              e.token
            ))) {
          Some(
            TokenBalanceAndAllowance(
              address = e.token,
              balance = e.balance,
              allowance = e.allowance,
              availableBalance = e.availableBalance,
              availableAllowance = e.allowance
            )
          )
        } else None
      case _ => None
    }

  }

}
