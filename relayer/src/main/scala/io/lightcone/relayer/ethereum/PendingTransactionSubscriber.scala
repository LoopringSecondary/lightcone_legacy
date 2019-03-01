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

package io.lightcone.relayer.ethereum

import java.net.URI
import java.util

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import io.lightcone.relayer.actors.PendingTxEventExtractorActor
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.data._
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthSubscribe
import org.web3j.protocol.websocket.events.PendingTransactionNotification
import org.web3j.protocol.websocket._
import io.lightcone.relayer.base._
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext

class PendingTransactionSubscriber(
    connectorActorName: String,
    settings: EthereumProxySettings.Node
  )(
    implicit
    val system: ActorSystem,
    val ec: ExecutionContext,
    actors: Lookup[ActorRef],
    val timeout: Timeout) {

  var client: WebSocketClient = null

  def start() = {
    system.scheduler.schedule(0 second, 5 second, new Runnable {
      override def run(): Unit = if (client == null || !client.isOpen) {
        subscribe()
      }
    })
  }

  def subscribe() = {

    client = new WebSocketClient(
      new URI(s"ws://${settings.host}:${settings.wsPort}")
    )

    val webSocketService =
      new WebSocketService(client, false)
    webSocketService.connect()

    val subscribeRequest = new Request[AnyRef, EthSubscribe](
      "eth_subscribe",
      util.Arrays.asList("newPendingTransactions"),
      webSocketService,
      classOf[EthSubscribe]
    )

    val events = webSocketService.subscribe(
      subscribeRequest,
      "eth_unsubscribe",
      classOf[PendingTransactionNotification]
    )
    events.subscribe((t: PendingTransactionNotification) => {
      if (actors.contains(connectorActorName)) {
        (actors.get(connectorActorName) ? GetTransactionByHash.Req(
          hash = t.getParams.getResult
        )).mapAs[GetTransactionByHash.Res]
          .map(
            res =>
              if (res.result.nonEmpty && actors
                    .contains(PendingTxEventExtractorActor.name)) {
                actors.get(PendingTxEventExtractorActor.name) ! res.result.get
              }
          )
      }
    })
  }

}
