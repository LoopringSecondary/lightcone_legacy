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

package io.lightcone.relayer.socketio

import io.lightcone.relayer.data._

// TODO(yadong): implement this.
class RelayerNotifier() extends SocketIONotifier {
  import SocketIOSubscription._

  def isSubscriptionValid(subscription: SocketIOSubscription): Boolean = ???

  def generateNotification(
      evt: AnyRef,
      subscription: SocketIOSubscription
    ): Option[Notification] = evt match {

    // case e: TokenMetadata =>
    //   if (subscription.paramsForTokenMetadata.isDefined) // TODO
    //     Some(Notifiation(tokenParams = Some(e)))
    //   else None

    case _ => None
  }

}
