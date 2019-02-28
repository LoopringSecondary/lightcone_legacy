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

import java.net.URI
import java.util

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.ethereum.HttpConnector
import javax.inject.Inject
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthSubscribe
import org.web3j.protocol.websocket._
import org.web3j.protocol.websocket.events.PendingTransactionNotification

import scala.concurrent.{ExecutionContext, Future}

object PendingTxListenerActor extends DeployedAsSingleton {

  val name = "pending_transaction_listener"

  def start(
      implicit
      config: Config,
      system: ActorSystem,
      ec: ExecutionContext,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new PendingTxListenerActor()))
  }

}

class PendingTxListenerActor @Inject()(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends InitializationRetryActor {

  val nodes = HttpConnector.connectorNames(config)

  override def initialize(): Future[Unit] = Future {
    nodes.foreach { node =>
      val client =
        new WebSocketClient(new URI(s"ws://${node._2.host}:${node._2.wsPort}"))
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
        (actors.get(node._1) ? GetTransactionByHash.Req(
          hash = t.getParams.getResult
        )).mapAs[GetTransactionByHash.Res]
          .map(
            res =>
              if (res.result.nonEmpty && actors.contains(node._1)) {
                self ! actors.get(node._1)
              }
          )
      })
    }
    becomeReady()
  }

  def ready: Receive = {
    case tx: Transaction =>
    //TODO(yadong) 解析Transaction，把事件发送到对应的Actor

  }

}
