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

package io.lightcone.relayer.support

import io.lightcone.relayer.actors.SocketIONotificationActor
import io.lightcone.relayer.data.SocketIOSubscription
import io.lightcone.relayer.socketio._
import io.lightcone.relayer.socketio.notifiers._

trait SocketSupport {
  com: CommonSpec =>
  implicit val accountNotifier = new AccountNotifier()
  implicit val activityNotifier = new ActivityNotifier()
  implicit val orderNotifier = new OrderNotifier()
  implicit val orderBookNotifier = new OrderBookNotifier()
  implicit val tickerNotifier = new TickerNotifier()
  implicit val fillNotifier = new FillNotifier()
  implicit val tokensNotifier = new TokensNotifier()
  implicit val marketsNotifier = new MarketNotifier()

  actors.add(SocketIONotificationActor.name, SocketIONotificationActor.start)

  val socketServer = new SocketServer()
  socketServer
    .addNotifier(
      classOf[SocketIOSubscription.ParamsForAccounts],
      accountNotifier
    )
    .addNotifier(
      classOf[SocketIOSubscription.ParamsForActivities],
      activityNotifier
    )
    .addNotifier(
      classOf[SocketIOSubscription.ParamsForFills],
      fillNotifier
    )
    .addNotifier(
      classOf[SocketIOSubscription.ParamsForOrders],
      orderNotifier
    )
    .addNotifier(
      classOf[SocketIOSubscription.ParamsForTickers],
      tickerNotifier
    )
    .addNotifier(
      classOf[SocketIOSubscription.ParamsForOrderbook],
      orderBookNotifier
    )
    .addNotifier(
      classOf[SocketIOSubscription.ParamsForTokens],
      tokensNotifier
    )
    .addNotifier(
      classOf[SocketIOSubscription.ParamsForMarkets],
      marketsNotifier
    )
    .start()

  println("start socket server......")
}
