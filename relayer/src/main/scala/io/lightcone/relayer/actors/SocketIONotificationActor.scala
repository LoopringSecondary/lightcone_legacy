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

package io.lightcone.relayer.actors

import akka.actor._
import akka.util.Timeout
import com.google.inject.Inject
import io.lightcone.relayer.base._
import io.lightcone.relayer.socketio._
import io.lightcone.core._
import io.lightcone.persistence._
import io.lightcone.relayer.data.cmc.ExternalMarketTickerInfo
import io.lightcone.relayer.data._

import scala.concurrent.ExecutionContext

object SocketIONotificationActor extends DeployedAsSingleton {
  val name = "socketio_notifier"

  def start(
      implicit
      system: ActorSystem,
      ec: ExecutionContext,
      timeout: Timeout,
      accountNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForAccounts
      ],
      activityNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForActivities
      ],
      orderNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForOrders
      ],
      fillNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForFills
      ],
      tickerNotifier: SocketIONotifier[SocketIOSubscription.ParamsForTickers],
      orderBookNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForOrderbook
      ],
      tokenMetadataNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForTokens
      ],
      marketMetadataNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForMarkets
      ],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new SocketIONotificationActor()))
  }
}

class SocketIONotificationActor @Inject()(
    implicit
    val system: ActorSystem,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val accountNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForAccounts
    ],
    val activityNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForActivities
    ],
    val orderNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForOrders
    ],
    val fillNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForFills
    ],
    val tickerNotifier: SocketIONotifier[SocketIOSubscription.ParamsForTickers],
    val orderBookNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForOrderbook
    ],
    val tokenNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForTokens
    ],
    val marketNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForMarkets
    ])
    extends Actor {

  def receive: Receive = {
    // events to deliver to socket.io clients must be generated here, not inside the listeners.
    //TODO(yadong)  需要把不展示到前端的字段清除
    case event: AccountUpdate =>
      accountNotifier.notifyEvent(event)
    case event: Activity =>
      activityNotifier.notifyEvent(event)
    case fill: Fill =>
      fillNotifier.notifyEvent(fill)
    case event: MarketMetadata =>
      marketNotifier.notifyEvent(event)
    case event: Orderbook.Update =>
      orderBookNotifier.notifyEvent(event)
    case event: RawOrder =>
      orderNotifier.notifyEvent(event)
    case event: TokenMetadataUpdate =>
      tokenNotifier.notifyEvent(event)
    case event: ExternalMarketTickerInfo =>
      tickerNotifier.notifyEvent(event)
  }
}
