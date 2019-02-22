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
import io.lightcone.relayer.data.{SocketIOSubscription, TokenMetadataUpdate}
import io.lightcone.relayer.socketio.{SocketIONotifier, SocketIOSubscriber}

class TokensNotifier @Inject()
    extends SocketIONotifier[SocketIOSubscription.ParamsForTokens] {

  val eventName: String = "token_metadata"

  def isSubscriptionValid(
      subscription: SocketIOSubscription.ParamsForTokens
    ): Boolean = true

  def wrapClient(
      client: SocketIOClient,
      subscription: SocketIOSubscription.ParamsForTokens
    ) =
    new SocketIOSubscriber(
      client,
      subscription
    )

  def extractNotifyData(
      subscription: SocketIOSubscription.ParamsForTokens,
      event: AnyRef
    ): Option[AnyRef] = {
    event match {
      case e: TokenMetadataUpdate =>
        Some(e)
      case _ => None
    }
  }

}
