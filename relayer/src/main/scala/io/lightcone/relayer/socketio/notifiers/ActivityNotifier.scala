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
import io.lightcone.lib._
import io.lightcone.persistence.Activity
import io.lightcone.relayer.data.SocketIOSubscription
import io.lightcone.relayer.socketio._

class ActivityNotifier @Inject()
    extends SocketIONotifier[SocketIOSubscription.ParamsForActivities] {

  val name = "transactions"

  def isSubscriptionValid(subscription: SocketIOSubscription): Boolean =
    subscription.paramsForActivities.isDefined && subscription.paramsForActivities.get.addresses.nonEmpty

  def wrapClient(
      client: SocketIOClient,
      req: SocketIOSubscription
    ) =
    req.paramsForActivities
      .map(
        params =>
          new SocketIOSubscriber(
            client,
            params.copy(addresses = params.addresses.map(Address.normalize))
          )
      )
      .get

  def shouldNotifyClient(
      subscription: SocketIOSubscription.ParamsForActivities,
      event: AnyRef
    ): Boolean = event match {
    case e: Activity => subscription.addresses.contains(e.owner)
    case _           => false
  }
}
