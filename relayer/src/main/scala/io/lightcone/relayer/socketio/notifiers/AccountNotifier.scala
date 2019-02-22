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
import io.lightcone.lib.Address
import io.lightcone.relayer.data.{AccountUpdate, SocketIOSubscription}
import io.lightcone.relayer.socketio._
import io.lightcone.core._

class AccountNotifier @Inject()
    extends SocketIONotifier[SocketIOSubscription.ParamsForAccounts] {

  val eventName = "accounts"

  def wrapClient(
      client: SocketIOClient,
      req: SocketIOSubscription.ParamsForAccounts
    ) =
    new SocketIOSubscriber(
      client,
      req.copy(
        addresses = req.addresses.map(Address.normalize),
        tokens = req.tokens.map(Address.normalize)
      )
    )

  def extractNotifyData(
      subscription: SocketIOSubscription.ParamsForAccounts,
      event: AnyRef
    ): Option[AnyRef] = {
    event match {
      case e: AccountUpdate =>
        if (subscription.addresses.contains(e.address)
            && (subscription.tokens.isEmpty || e.tokenBalance.isEmpty || subscription.tokens
              .contains(
                e.getTokenBalance.token
              ))) {
          Some(
            AccountInfo(
              address = e.address,
              nonce = e.nonce,
              tokenBalance = e.tokenBalance.map(
                tb =>
                  TokenBalanceAndAllowance(
                    token = tb.token,
                    balance = tb.getBalance,
                    allowance = tb.getAllowance,
                    availableBalance = tb.getAvailableBalance,
                    availableAllowance = tb.availableAlloawnce
                  )
              )
            )
          )
        } else None
      case _ => None
    }

  }

}
