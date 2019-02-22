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

import io.lightcone.relayer.socketio._
import io.lightcone.relayer.socketio.notifiers._

trait SocketSupport {
  com: CommonSpec =>
  implicit val balancelistener = new AccountNotifier()
  implicit val transactionNotifier = new ActivityNotifier()
  implicit val orderNotifier = new OrderNotifier()
  implicit val orderBookNotifier = new OrderBookNotifier()
  implicit val tickerNotifier = new TickerNotifier()
  implicit val tradeNotifier = new FillNotifier()
  implicit val transferNotifier = new TransferNotifier()

  val socketServer = new SocketServer()
  socketServer.start()

  println("start socket server......")
}
