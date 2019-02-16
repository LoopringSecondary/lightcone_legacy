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

package io.lightcone.relayer.socket.Listeners

import akka.actor.ActorSystem
import com.corundumstudio.socketio.{AckRequest, SocketIOClient}
import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.core.{Address, _}
import io.lightcone.relayer.data._
import io.lightcone.relayer.socket._

import scala.concurrent.ExecutionContext

object BalanceListener {
  val eventName = "balances"
}

class BalanceListener @Inject()(
    implicit
    val system: ActorSystem,
    val ec: ExecutionContext,
    val config: Config)
    extends WrappedDataListener[SubcribeBalanceAndAllowance.Req] {

  def onData(
      client: SocketIOClient,
      data: SubcribeBalanceAndAllowance.Req,
      ackSender: AckRequest
    ): Unit = {
    if (ackSender.isAckRequested) {
      ackSender.sendAckData("subscribe for balances successfully")
    }
    val wrappedSocketClient =
      new WrappedSocketClient(
        BalanceListener.eventName,
        client,
        data.copy(
          addresses = data.addresses.map(Address.normalize),
          tokens = data.tokens.map(Address.normalize)
        )
      )
    clients =
      clients.dropWhile(wrappedSocketClient.equals).+:(wrappedSocketClient)
  }

  def dealDataChanged(msg: Any): Unit = {
    msg match {
      case res: GetBalanceAndAllowances.Res =>
        clients.foreach { client =>
          if (client.req.addresses.exists(res.address.equals)
              && (client.req.tokens
                .intersect(res.balanceAndAllowanceMap.keys.toSeq)
                .nonEmpty || client.req.tokens.isEmpty)) {
            val data = BalanceAndAllowanceResponse(
              owner = res.address,
              balanceAndAllowances = res.balanceAndAllowanceMap.filter { ba =>
                client.req.tokens.isEmpty || client.req.tokens.exists(
                  ba._1.equals
                )
              }.map { ba =>
                TokenBalanceAndAllowance(
                  address = ba._1,
                  balance = ba._2.balance,
                  allowance = ba._2.allowance,
                  availableBalance = ba._2.balance,
                  availableAllowance = ba._2.allowance
                )
              }.toSeq
            )
            client.sendEvent(data)
          }
        }
      case _ =>
    }
  }
}
