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
import io.lightcone.ethereum.event._
import io.lightcone.lib.NumericConversion

import scala.concurrent.ExecutionContext

object SocketIONotificationActor extends DeployedAsSingleton {
  val name = "socketio_notifier"

  def start(
      implicit
      system: ActorSystem,
      ec: ExecutionContext,
      timeout: Timeout,
      balanceNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForBalanceUpdate
      ],
      transactionNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForTxRecord
      ],
      orderNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForOrderUpdate
      ],
      tickerNotifier: SocketIONotifier[SocketIOSubscription.ParamsForTicker],
      orderBookNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForOrderbookUpdate
      ],
      tokenMetadataNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForTokenMetadata
      ],
      marketMetadataNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForMarketMetadata
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
    val balanceNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForBalanceUpdate
    ],
    val transactionNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForTxRecord
    ],
    val orderNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForOrderUpdate
    ],
    val tickerNotifier: SocketIONotifier[SocketIOSubscription.ParamsForTicker],
    val orderBookNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForOrderbookUpdate
    ],
    val tokenMetadataNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForTokenMetadata
    ],
    val marketMetadataNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForMarketMetadata
    ])
    extends Actor {

  def receive: Receive = {
    // events to deliver to socket.io clients must be generated here, not inside the listeners.
    case event: BalanceUpdate =>
      balanceNotifier.notifyEvent(event)

    case event: TxRecordUpdate =>
      transactionNotifier.notifyEvent(event)

    case event: OrderUpdate =>
      orderNotifier.notifyEvent(event)

  }
}
