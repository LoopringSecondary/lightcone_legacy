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
import io.lightcone.persistence.Activity
import io.lightcone.relayer.data.SocketIOSubscription

class ActivityNotifier @Inject()
    extends SocketIONotifier[SocketIOSubscription.ParamsForActivities] {

  val eventName = "transactions"

  def wrapClient(
      client: SocketIOClient,
      req: SocketIOSubscription.ParamsForActivities
    ) =
    new SocketIOSubscriber(
      client,
      req.copy(addresses = req.addresses.map(Address.normalize))
    )

  def extractNotifyData(
      subscription: SocketIOSubscription.ParamsForActivities,
      event: AnyRef
    ): Option[AnyRef] =
    event match {
      case e: Activity =>
        if (subscription.addresses.contains(e.owner)) {
          Some()
        } else None
      case _ => None
    }
}
